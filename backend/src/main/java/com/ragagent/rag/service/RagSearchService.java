package com.ragagent.rag.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDocumentChunkMapper;
import com.ragagent.rag.mapper.AiDocumentMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * <h1>RAG 知识库多维检索服务 (RAG Search Service)</h1>
 * <p>
 * 本服务负责在多租户、细粒度权限控制前提下，实现高精度、高可用的知识切片检索与重排。
 * 核心架构特性与学习要点：
 * <ol>
 *   <li><b>权限过滤下推 (Filter Expression Pushdown)</b>：
 *       <br>在向量检索中，绝对不能先从向量库召回 Top-K，再在应用层过滤无权访问的知识库切片（这样会导致大量合法候选被挤出 Top-K，产生“召回黑洞”）。
 *       必须在调用底层向量数据库（如 pgvector、Milvus）时，将 {@code datasetId in [...]} 条件直接下推到底层索引过滤执行。</li>
 *   <li><b>两阶段架构 (Two-Stage Retrieval: Recall + Rerank)</b>：
 *       <br>第一阶段（粗排）：适度放宽相似度阈值（如 0.35），多倍（Top-K * 3）召回候选切片，保证高召回率（Recall）。
 *       <br>第二阶段（精排）：结合词频覆盖度（BM25 词频拟合）、精确连续匹配率进行细粒度重排序，保证高准确率（Precision）。</li>
 *   <li><b>动态弹性降级 (Graceful Fallback)</b>：
 *       <br>若向量库因网络抖动、模型配额耗尽等原因检索异常，系统将自动回退至关系型数据库的全文/模糊匹配，确保业务系统永不中断。</li>
 * </ol>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    /** 知识库元数据持久层访问接口 */
    private final AiDatasetMapper datasetMapper;
    /** 文档主表 Mapper */
    private final AiDocumentMapper documentMapper;
    /** 切片明细表 Mapper */
    private final AiDocumentChunkMapper chunkMapper;
    /** Spring AI 向量库提供者 (支持动态按需注入) */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /** 是否启用远程向量模型 (可配置关闭以仅使用关系库全文检索) */
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;

    /**
     * 统一检索命中切片实体 (Search Result Chunk)
     */
    @Data
    @Builder
    public static class SearchResultChunk {
        /** 切片主键 ID */
        private Long chunkId;
        /** 所属知识库 ID */
        private Long datasetId;
        /** 所属文档主键 ID */
        private Long documentId;
        /** 所属文档原始文件名 (用于溯源展示) */
        private String documentName;
        /** 匹配的文本正文切片 */
        private String content;
        /** 归一化综合相关度得分 (0.0 ~ 1.0) */
        private Double score;
    }

    /**
     * <h3>执行带权限隔离的混合知识库检索 (默认上下文重载)</h3>
     * <p>
     * 自动从当前安全上下文提取登录用户的 ID（若未登录或处于非 Web 线程则降级为默认管理员 1L）。
     *
     * @param query            用户输入的原始提问或检索词
     * @param targetDatasetIds 用户指定的检索范围 (若为 null 或空列表，则在当前用户有权访问的所有知识库中跨库检索)
     * @param topK             期望召回的最佳切片条数
     * @return 排序后的切片列表
     */
    public List<SearchResultChunk> search(String query, List<Long> targetDatasetIds, int topK) {
        Long userId = com.ragagent.common.security.SecurityUtils.getLoginUserIdOrDefault(1L);
        return search(userId, query, targetDatasetIds, topK);
    }

    /**
     * <h3>执行带权限隔离的混合知识库检索 (显式传递用户 ID 核心方法)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li>在 Spring WebFlux 响应式流或虚拟线程池中，由于线程切换，{@code ThreadLocal} 可能丢失；显式传递 {@code userId} 能够彻底根除跨线程上下文丢失的隐患。</li>
     * </ul>
     *
     * @param userId           当前发起检索的用户 ID
     * @param query            检索提问
     * @param targetDatasetIds 目标知识库 ID 列表
     * @param topK             召回上限数量
     * @return 最终召回切片
     */
    public List<SearchResultChunk> search(Long userId, String query, List<Long> targetDatasetIds, int topK) {
        if (userId == null) {
            userId = 1L;
        }

        // 步骤 1：权限隔离决策 —— 查询当前用户在 RBAC 权限体系下所有被授权访问的知识库
        List<AiDataset> accessibleDatasets = datasetMapper.selectAccessibleDatasets(userId);
        Set<Long> allowedDatasetIdSet = accessibleDatasets.stream().map(AiDataset::getId).collect(Collectors.toSet());

        // 计算最终生效的知识库 ID 集合（求请求范围与授权范围的交集，防止越权访问未授权知识库）
        List<Long> effectiveDatasetIds = new ArrayList<>();
        if (targetDatasetIds != null && !targetDatasetIds.isEmpty()) {
            for (Long reqId : targetDatasetIds) {
                if (allowedDatasetIdSet.contains(reqId)) {
                    effectiveDatasetIds.add(reqId);
                }
            }
        } else {
            effectiveDatasetIds.addAll(allowedDatasetIdSet);
        }

        // 安全拦截：如果用户无权访问任何目标知识库，立即快速返回空结果，避免无意义的向量库计算
        if (effectiveDatasetIds.isEmpty()) {
            log.warn("【RagSearch】用户 [{}] 无权访问任何目标知识库，检索终止", userId);
            return Collections.emptyList();
        }

        List<SearchResultChunk> results = new ArrayList<>();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();

        // 步骤 2：第一阶段 —— 向量语义粗排检索 (利用 Filter Expression 下推过滤)
        if (embeddingEnabled && vectorStore != null) {
            try {
                // 两阶段优化：将粗排候选集放大至 Top-K * 3（至少 10 条），为精排提供充足的候选池
                int roughTopK = Math.max(topK * 3, 10);
                
                // 构造底层向量数据库过滤表达式：datasetId in ['1', '2']
                String filterExp = null;
                if (!effectiveDatasetIds.isEmpty()) {
                    String idsJoin = effectiveDatasetIds.stream()
                            .map(id -> "'" + id + "'")
                            .collect(Collectors.joining(", "));
                    filterExp = "datasetId in [" + idsJoin + "]";
                }

                // 组装 Spring AI 2.0 向量检索请求
                SearchRequest.Builder requestBuilder = SearchRequest.builder()
                        .query(query)
                        .topK(roughTopK)
                        .similarityThreshold(0.35); // 适度调低初筛阈值，避免把语义相近但措辞不同的优质切片提前丢弃

                if (StringUtils.hasText(filterExp)) {
                    requestBuilder.filterExpression(filterExp);
                }

                List<Document> simDocs = vectorStore.similaritySearch(requestBuilder.build());
                List<SearchResultChunk> candidateList = new ArrayList<>();

                for (Document doc : simDocs) {
                    Object dsId = doc.getMetadata().get("datasetId");
                    Long docDatasetId = dsId != null ? Long.valueOf(dsId.toString()) : null;
                    // 应用层二次核验权限隔离，实现零信任架构双保险
                    if (docDatasetId != null && effectiveDatasetIds.contains(docDatasetId)) {
                        String docText = doc.getText();
                        // 步骤 3：第二阶段 —— Rerank 精排打分（综合语义与精确匹配）
                        double baseScore = calculateRelevanceScore(query, docText);
                        candidateList.add(SearchResultChunk.builder()
                                .datasetId(docDatasetId)
                                .documentName(String.valueOf(doc.getMetadata().getOrDefault("docName", "未知文档")))
                                .content(docText)
                                .score(baseScore)
                                .build());
                    }
                }

                // 精排重排 (Rerank)：按复合综合得分进行降序重排，并精准截取前 Top-K 条
                candidateList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                results.addAll(candidateList.stream().limit(topK).toList());

            } catch (Exception e) {
                // 容错降级日志记录
                log.warn("【RagSearch】向量库检索出现异常，自动降级启用数据库全文与模糊检索: {}", e.getMessage());
            }
        }

        // 步骤 4：弹性兜底 / 混合补充 —— 若向量检索召回数量不足 Top-K，从关系库分块表中做精确词频匹配补齐
        if (results.size() < topK) {
            int need = topK - results.size();
            List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                    .in(AiDocumentChunk::getDatasetId, effectiveDatasetIds)
                    .like(AiDocumentChunk::getContent, query.length() > 6 ? query.substring(0, 6) : query)
                    .last("LIMIT " + need));

            for (AiDocumentChunk chunk : chunks) {
                AiDocument doc = documentMapper.selectById(chunk.getDocumentId());
                results.add(SearchResultChunk.builder()
                        .chunkId(chunk.getId())
                        .datasetId(chunk.getDatasetId())
                        .documentId(chunk.getDocumentId())
                        .documentName(doc != null ? doc.getName() : "知识文档")
                        .content(chunk.getContent())
                        .score(0.70) // 给予基础默认匹配置信分
                        .build());
            }
        }

        return results;
    }

    /**
     * <h3>Rerank 精排相关度评分算法 (Relevance Scoring Algorithm)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li>向量模型（如 text-embedding-3-small）仅衡量余弦角度，对“是否完整包含关键实体词”不敏感。</li>
     *   <li>本算法结合<b>完全子串包含</b>与<b>分词覆盖度比率</b>进行二次加权打分，将得分映射在 0.0 ~ 0.99 之间，大幅提升大模型引用的准确性。</li>
     * </ul>
     *
     * @param query 用户提问词
     * @param text  待评估的候选切片正文
     * @return 最终相关度打分 (0.0 ~ 0.99)
     */
    private double calculateRelevanceScore(String query, String text) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
            return 0.5;
        }
        double score = 0.65; // 向量召回的基础合格底分
        if (text.contains(query)) {
            // 完整包含整句提问，给予极高加权 (+0.25)
            score += 0.25;
        } else {
            // 将长句拆分为关键词子单元，计算词项覆盖率 (Term Coverage)
            int matchCount = 0;
            String[] terms = query.split("\\s+|，|。|？|！|,|\\?");
            for (String term : terms) {
                if (StringUtils.hasText(term) && text.contains(term.trim())) {
                    matchCount++;
                }
            }
            if (terms.length > 0) {
                // 根据命中的词汇比例给予梯度奖励得分 (+0.0 ~ +0.20)
                score += 0.20 * ((double) matchCount / terms.length);
            }
        }
        return Math.min(score, 0.99);
    }
}
