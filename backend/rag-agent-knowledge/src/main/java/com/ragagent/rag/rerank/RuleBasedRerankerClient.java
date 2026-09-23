package com.ragagent.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <h3>本地规则加权重排器 (Rule-Based Fallback Reranker)</h3>
 * <p>
 * <b>学习核心知识点：</b>
 * <ul>
 *   <li>向量相似度（余弦夹角）对“专有名词、核心编号、金额”等精确字符缺乏敏感度；</li>
 *   <li>本组件结合<b>整句完全包含奖励 (+0.25)</b> 与 <b>中文分词/标点切分覆盖率 (+0.0~0.20)</b> 进行二次复合加权；</li>
 *   <li>纯本地 CPU 计算，耗时 < 1ms，在远程 Reranker 网络超时或模型离线时作为 100% 稳定的高可用兜底。</li>
 * </ul>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Component("ruleBasedRerankerClient")
public class RuleBasedRerankerClient implements RerankerClient {

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<RerankResult> results = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            String text = documents.get(i);
            double score = calculateScore(query, text);
            results.add(RerankResult.builder()
                    .index(i)
                    .text(text)
                    .score(score)
                    .build());
        }

        // 按置信得分降序排列
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        if (topN != null && topN > 0 && topN < results.size()) {
            return results.subList(0, topN);
        }
        return results;
    }

    @Override
    public String getType() {
        return "RULE_BASED";
    }

    /**
     * 启发式词项覆盖与完全匹配加权计算
     */
    public double calculateScore(String query, String text) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
            return 0.5;
        }

        double score = 0.65; // 向量召回的基础合格底分
        if (text.contains(query)) {
            score += 0.25; // 完整包含提问原句给予重大奖励
        } else {
            // 将提问按照常见中英文标点与空白进行轻量切词
            String[] terms = query.split("[\\s+，。？！,?!；;、]+");
            int matchCount = 0;
            int validTerms = 0;
            for (String term : terms) {
                if (StringUtils.hasText(term)) {
                    validTerms++;
                    if (text.contains(term.trim())) {
                        matchCount++;
                    }
                }
            }
            if (validTerms > 0) {
                score += 0.20 * ((double) matchCount / validTerms);
            }
        }
        return Math.min(score, 0.99);
    }
}
