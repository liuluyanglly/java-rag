package com.ragagent.rag.rerank;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ragagent.rag.config.RerankerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h3>BGE HTTP 标准重排序客户端 (BGE Cross-Encoder HTTP Reranker)</h3>
 * <p>
 * <b>学习核心知识点：</b>
 * <ul>
 *   <li><b>为什么需要 Cross-Encoder？</b> 传统的双塔（Bi-Encoder）Embedding 分别对 Query 与 Doc 计算向量并计算点积，缺少词与词之间的交互；
 *       Cross-Encoder 将 (Query, Document) 成对拼接输入 Transformer 的所有自注意力层，直接建模深层交叉逻辑，精准识别否定词、限定语与条件词。</li>
 *   <li><b>协议兼容性</b>：完美兼容 HuggingFace TEI (Text Embeddings Inference)、Xinference、vLLM、Ollama 以及 Cohere 的 {@code /v1/rerank} 或 {@code /rerank} 协议。</li>
 *   <li><b>熔断与弹性降级</b>：当 HTTP 请求发生超时、连接被拒或非 200 响应时，自动平滑无感地降级至 {@link RuleBasedRerankerClient}。</li>
 * </ul>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Primary
@Component("bgeHttpRerankerClient")
@RequiredArgsConstructor
public class BgeHttpRerankerClient implements RerankerClient {

    private final RerankerProperties properties;
    private final RuleBasedRerankerClient fallbackClient;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        // 若未启用远程 Reranker，或者未配置 BaseUrl，则平滑使用本地规则重排
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getBaseUrl())) {
            log.debug("【Reranker】远程重排未启用，采用本地规则打分");
            return fallbackClient.rerank(query, documents, topN);
        }

        try {
            List<RerankResult> results = doHttpRerank(query, documents, topN);
            if (results != null && !results.isEmpty()) {
                return results;
            }
        } catch (Exception e) {
            log.warn("【Reranker】调用远程重排服务 [{}] 失败: {}，自动降级启用本地规则重排", properties.getBaseUrl(), e.getMessage());
        }

        return fallbackClient.rerank(query, documents, topN);
    }

    @Override
    public String getType() {
        return "BGE_HTTP";
    }

    /**
     * 发起标准 HTTP Rerank 请求 (兼容 TEI / Xinference / Cohere / FastChat)
     */
    private List<RerankResult> doHttpRerank(String query, List<String> documents, Integer topN) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        String targetUrl = baseUrl.endsWith("/rerank") ? baseUrl : (baseUrl + "/v1/rerank");

        Map<String, Object> payload = new HashMap<>();
        if (StringUtils.hasText(properties.getModel())) {
            payload.put("model", properties.getModel());
        }
        payload.put("query", query);
        payload.put("documents", documents);
        if (topN != null && topN > 0) {
            payload.put("top_n", topN);
        }
        payload.put("return_documents", false);

        HttpRequest request = HttpRequest.post(targetUrl)
                .body(JSONUtil.toJsonStr(payload))
                .header("Content-Type", "application/json")
                .timeout(properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 5000);

        if (StringUtils.hasText(properties.getApiKey()) && !"empty".equalsIgnoreCase(properties.getApiKey())) {
            request.header("Authorization", "Bearer " + properties.getApiKey());
        }

        try (HttpResponse response = request.execute()) {
            // 若 /v1/rerank 404，尝试回退探测 /rerank
            if (response.getStatus() == 404 && targetUrl.endsWith("/v1/rerank")) {
                String fallbackUrl = baseUrl + "/rerank";
                log.info("【Reranker】/v1/rerank 404，尝试使用端点: {}", fallbackUrl);
                request.setUrl(fallbackUrl);
                try (HttpResponse retryResp = request.execute()) {
                    return parseResponse(retryResp, documents, topN);
                }
            }
            return parseResponse(response, documents, topN);
        }
    }

    private List<RerankResult> parseResponse(HttpResponse response, List<String> documents, Integer topN) {
        if (response.getStatus() != 200) {
            log.warn("【Reranker】服务返回非 200 状态码: status={}, body={}", response.getStatus(), response.body());
            return null;
        }

        String body = response.body();
        if (!StringUtils.hasText(body)) {
            return null;
        }

        List<RerankResult> list = new ArrayList<>();
        if (JSONUtil.isTypeJSONObject(body)) {
            JSONObject json = JSONUtil.parseObj(body);
            // 匹配 results 数组 (Cohere / TEI / Xinference 规范)
            if (json.containsKey("results")) {
                JSONArray arr = json.getJSONArray("results");
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    int idx = item.getInt("index", i);
                    double score = item.getDouble("relevance_score", item.getDouble("score", 0.0));
                    String text = idx >= 0 && idx < documents.size() ? documents.get(idx) : "";
                    list.add(RerankResult.builder().index(idx).score(score).text(text).build());
                }
            } else if (json.containsKey("data")) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    int idx = item.getInt("index", i);
                    double score = item.getDouble("score", item.getDouble("relevance_score", 0.0));
                    String text = idx >= 0 && idx < documents.size() ? documents.get(idx) : "";
                    list.add(RerankResult.builder().index(idx).score(score).text(text).build());
                }
            }
        } else if (JSONUtil.isTypeJSONArray(body)) {
            // 直接返回数组的情形
            JSONArray arr = JSONUtil.parseArray(body);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                int idx = item.getInt("index", i);
                double score = item.getDouble("score", item.getDouble("relevance_score", 0.0));
                String text = idx >= 0 && idx < documents.size() ? documents.get(idx) : "";
                list.add(RerankResult.builder().index(idx).score(score).text(text).build());
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        // 统一按置信得分降序排列
        list.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 结合置信度底线阈值进行自适应过滤
        double minScore = properties.getMinScore();
        if (minScore > 0) {
            list = list.stream().filter(r -> r.getScore() >= minScore).toList();
        }

        if (topN != null && topN > 0 && topN < list.size()) {
            return list.subList(0, topN);
        }
        return list;
    }
}
