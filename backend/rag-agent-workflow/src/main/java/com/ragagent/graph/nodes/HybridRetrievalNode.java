package com.ragagent.graph.nodes;

import com.ragagent.graph.GraphNode;
import com.ragagent.graph.GraphState;
import com.ragagent.rag.service.HybridSearchEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产级多维混合检索节点 (HybridRetrievalNode)
 * 支持自适应意图检索（优先使用 QueryRewrite 重写后的关键词进行二轮深搜），
 * 并进行多轮证据链增量累积与智能去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrievalNode implements GraphNode {

    private final HybridSearchEngine hybridSearchEngine;

    @Override
    public GraphState execute(GraphState state) {
        // 优先使用自愈重构后的查询，若无则使用原课题
        String query = state.get("currentSearchQuery") != null
                ? String.valueOf(state.get("currentSearchQuery"))
                : state.getTopic();

        int retryCount = (Integer) state.getData().getOrDefault("retryCount", 0);
        log.info("[HybridRetrievalNode] 执行混合检索 (第 {} 轮): query='{}'", retryCount, query);

        if (retryCount > 0) {
            state.addThought("🔎 启动第 " + retryCount + " 轮自适应拓展搜研: 针对重构关键词【" + query + "】展开 Dense 向量 + BM25 复合匹配...");
        } else {
            state.addThought("🔎 启动四维检索中枢 (Dense 向量 + BM25 关键词 + 图谱拓扑网络 + 实时线索)...");
        }

        List<HybridSearchEngine.HybridEvidence> newEvidences = hybridSearchEngine.search(query, null, 5);

        // 证据去重集合
        Set<String> existingSnippets = new HashSet<>();
        if (state.getEvidences() != null) {
            for (var ev : state.getEvidences()) {
                existingSnippets.add(String.valueOf(ev.get("snippet")));
            }
        }

        int addedCount = 0;
        for (var ev : newEvidences) {
            if (!existingSnippets.contains(ev.getSnippet())) {
                Map<String, Object> evMap = new HashMap<>();
                evMap.put("source", ev.getSource());
                evMap.put("title", ev.getTitle());
                evMap.put("snippet", ev.getSnippet());
                evMap.put("score", ev.getRrfScore());
                state.getEvidences().add(evMap);
                existingSnippets.add(ev.getSnippet());
                addedCount++;
            }
        }

        if (retryCount > 0) {
            state.addThought("📚 自愈检索完毕：成功增量补充 " + addedCount + " 条关联证据，当前总证据池已沉淀 " + state.getEvidences().size() + " 项。");
        } else {
            state.addThought("📚 成功捕获并经 RRF 融合重排归纳了 " + state.getEvidences().size() + " 条高置信度多维证据。");
        }

        return state;
    }
}
