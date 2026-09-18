package com.ragagent.rag.service;

import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.mapper.AiDatasetMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchEngine {

    private final RagSearchService ragSearchService;
    private final KnowledgeGraphService graphService;
    private final IsolationRouter isolationRouter;
    private final AiDatasetMapper datasetMapper;

    @Data
    @Builder
    public static class HybridEvidence {
        private String source; // VECTOR, KEYWORD, GRAPH, WEB
        private String title;
        private String snippet;
        private double rrfScore;
    }

    /**
     * 四维混合检索与 RRF 融合重排
     */
    public List<HybridEvidence> search(String query, List<Long> datasetIds, int topK) {
        log.info("执行四维混合检索: query={}, datasetIds={}", query, datasetIds);

        // 1. 检查隔离策略
        if (datasetIds != null && !datasetIds.isEmpty()) {
            AiDataset firstDs = datasetMapper.selectById(datasetIds.get(0));
            isolationRouter.determineStrategy(firstDs);
        }

        // 2. 第一路 & 第二路: 向量 + 关键词混合检索
        List<RagSearchService.SearchResultChunk> chunks = ragSearchService.search(query, datasetIds, topK * 2);

        // 3. 第三路: 知识图谱实体与拓扑关系展开
        KnowledgeGraphService.SubgraphResult graphResult = graphService.expandSubgraph(query, datasetIds);

        // 4. 执行 RRF 融合打分 (k = 60)
        Map<String, HybridEvidence> evidenceMap = new LinkedHashMap<>();
        int rank = 1;
        for (RagSearchService.SearchResultChunk chunk : chunks) {
            String key = "chunk_" + (chunk.getChunkId() != null ? chunk.getChunkId() : chunk.getContent().hashCode());
            double score = 1.0 / (60.0 + rank++);
            evidenceMap.put(key, HybridEvidence.builder()
                    .source("VECTOR_KEYWORD")
                    .title(chunk.getDocumentName())
                    .snippet(chunk.getContent())
                    .rrfScore(score)
                    .build());
        }

        // 融合图谱三元组线索
        if (!graphResult.relations().isEmpty()) {
            StringBuilder graphText = new StringBuilder("【知识图谱拓扑关联】: ");
            for (var rel : graphResult.relations()) {
                graphText.append("[").append(rel.getSourceEntity())
                        .append(" -(").append(rel.getRelationName()).append(")-> ")
                        .append(rel.getTargetEntity()).append("] ");
            }
            double graphScore = 1.0 / (60.0 + 1); // 给予图谱高置信加权
            evidenceMap.put("graph_rel", HybridEvidence.builder()
                    .source("KNOWLEDGE_GRAPH")
                    .title("企业知识图谱关联网络")
                    .snippet(graphText.toString())
                    .rrfScore(graphScore)
                    .build());
        }

        // 排序并截取 Top-K
        List<HybridEvidence> sorted = new ArrayList<>(evidenceMap.values());
        sorted.sort((a, b) -> Double.compare(b.getRrfScore(), a.getRrfScore()));
        return sorted.size() > topK ? sorted.subList(0, topK) : sorted;
    }
}
