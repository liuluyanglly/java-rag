package com.ragagent.rag.service;

import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.mapper.AiDatasetMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * <h1>四维混合检索引擎 (Hybrid Search Engine with RRF)</h1>
 * <p>
 * 本类是企业级 RAG 架构中最核心的检索调度中枢，整合了四维证据召回与融合排序能力：
 * <ul>
 *   <li><b>Dense 向量语义检索</b>：基于嵌入模型（Embedding）捕获高维语义相似度，擅长理解同义词、意图与隐式关联。</li>
 *   <li><b>Sparse BM25 关键词检索</b>：精确匹配专有名词、技术术语、编号、类名等精确词频特征，弥补向量对专有名词不敏感的缺陷。</li>
 *   <li><b>Knowledge Graph 知识图谱拓扑展开</b>：提取实体与实体之间的多跳（Multi-hop）关系，为大模型提供结构化逻辑因果链条。</li>
 *   <li><b>RRF (Reciprocal Rank Fusion, 倒数排名融合算法)</b>：无监督的排名融合标准算法，公式为 {@code Score = 1 / (k + rank)}，避免不同检索源得分基准不一致的问题。</li>
 * </ul>
 *
 * @author Java-RAG Team
 * @see RagSearchService
 * @see KnowledgeGraphService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchEngine {

    /** 向量检索与全文检索服务底层实现 */
    private final RagSearchService ragSearchService;
    /** 知识图谱三元组实体关系检索与拓扑展开服务 */
    private final KnowledgeGraphService graphService;
    /** 知识库多租户/角色级物理与逻辑隔离路由器 */
    private final IsolationRouter isolationRouter;
    /** 知识库元数据持久层访问接口 */
    private final AiDatasetMapper datasetMapper;

    /**
     * 混合检索最终统一证据结构体 (Hybrid Evidence DTO)
     * <p>
     * 无论切片来源于向量库、倒排索引还是知识图谱，最终均归一化为此统一结构，供 Agent 状态机或提示词组装。
     */
    @Data
    @Builder
    public static class HybridEvidence {
        /** 证据来源标识：VECTOR_KEYWORD（切片文本）、KNOWLEDGE_GRAPH（图谱三元组）、WEB（外网实时搜索） */
        private String source;
        /** 证据来源标题或文档名称 */
        private String title;
        /** 证据核心文本切片内容或图谱展开文字 */
        private String snippet;
        /** RRF 倒数排名融合归一化综合得分 */
        private double rrfScore;
    }

    /**
     * <h3>执行四维混合检索并应用 RRF 倒数排名融合</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ol>
     *   <li><b>为什么需要多路召回？</b> 单一的向量相似度经常出现“语义相关但关键数字/专有名词错误”的情况，混合召回能够实现精准匹配与意图泛化的优势互补。</li>
     *   <li><b>什么是 RRF (k=60)？</b> 倒数排名融合（Reciprocal Rank Fusion）是信息检索领域被广泛验证的算法。常数 k（通常取 60）用于平滑不同位次间的评分衰减，第 1 名得分为 1/(60+1)≈0.01639，第 2 名得分为 1/(60+2)≈0.01612，避免单个通道的极高绝对分数“劫持”最终结果。</li>
     * </ol>
     *
     * @param query      用户原始提问或重写后的检索查询词
     * @param datasetIds 允许检索的目标知识库 ID 集合（支持跨知识库联邦检索）
     * @param topK       最终召回并提交给 LLM 的切片上限数
     * @return 经过 RRF 融合打分并按置信度降序排列的高价值证据列表
     */
    public List<HybridEvidence> search(String query, List<Long> datasetIds, int topK) {
        log.info("【HybridSearchEngine】启动四维多路召回流水线: query='{}', datasetIds={}, topK={}", query, datasetIds, topK);

        // 步骤 1：租户安全与隔离策略决策 (判断是独立租户Schema还是逻辑隔离)
        if (datasetIds != null && !datasetIds.isEmpty()) {
            AiDataset firstDs = datasetMapper.selectById(datasetIds.get(0));
            if (firstDs != null) {
                isolationRouter.determineStrategy(firstDs);
            }
        }

        // 步骤 2：第一路 & 第二路并发检索（Dense 向量相似度 + Sparse 关键词全文匹配），提前多倍（topK * 2）候选召回
        List<RagSearchService.SearchResultChunk> chunks = ragSearchService.search(query, datasetIds, topK * 2);

        // 步骤 3：第三路拓扑图谱展开（实体命名识别 + 多跳关系图谱检索），获取结构化三元组
        KnowledgeGraphService.SubgraphResult graphResult = graphService.expandSubgraph(query, datasetIds);

        // 步骤 4：初始化证据融合映射池，基于 RRF 算法计算排序得分 (Reciprocal Rank Fusion: 1 / (60 + rank))
        Map<String, HybridEvidence> evidenceMap = new LinkedHashMap<>();
        int rank = 1;
        for (RagSearchService.SearchResultChunk chunk : chunks) {
            String key = "chunk_" + (chunk.getChunkId() != null ? chunk.getChunkId() : chunk.getContent().hashCode());
            // 依据候选结果的原始排名计算 RRF 权重
            double score = 1.0 / (60.0 + rank++);
            evidenceMap.put(key, HybridEvidence.builder()
                    .source("VECTOR_KEYWORD")
                    .title(chunk.getDocumentName())
                    .snippet(chunk.getContent())
                    .rrfScore(score)
                    .build());
        }

        // 步骤 5：将知识图谱的关系链条作为高置信度实体网络注入证据流
        if (graphResult != null && !graphResult.relations().isEmpty()) {
            StringBuilder graphText = new StringBuilder("【知识图谱拓扑关联】: ");
            for (var rel : graphResult.relations()) {
                graphText.append("[").append(rel.getSourceEntity())
                        .append(" -(").append(rel.getRelationName()).append(")-> ")
                        .append(rel.getTargetEntity()).append("] ");
            }
            // 知识图谱由明确三元组构成，赋予首位级别的高置信加权 (rank = 1)
            double graphScore = 1.0 / (60.0 + 1);
            evidenceMap.put("graph_rel", HybridEvidence.builder()
                    .source("KNOWLEDGE_GRAPH")
                    .title("企业知识图谱关联网络")
                    .snippet(graphText.toString())
                    .rrfScore(graphScore)
                    .build());
        }

        // 步骤 6：按 RRF 最终得分执行全局降序重排，并精准截取 Top-K 证据返回
        List<HybridEvidence> sorted = new ArrayList<>(evidenceMap.values());
        sorted.sort((a, b) -> Double.compare(b.getRrfScore(), a.getRrfScore()));
        return sorted.size() > topK ? sorted.subList(0, topK) : sorted;
    }
}
