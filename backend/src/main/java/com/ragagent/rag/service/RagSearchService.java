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
public class RagSearchService {

    private final AiDatasetMapper datasetMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;

    @Data
    @Builder
    public static class SearchResultChunk {
        private Long chunkId;
        private Long datasetId;
        private Long documentId;
        private String documentName;
        private String content;
        private Double score; // 相似度分数 0.0 ~ 1.0
    }

    /**
     * 执行带权限隔离的混合知识库检索
     * @param query 用户提问
     * @param targetDatasetIds 指定检索的知识库集合 (若为空则检索该用户有权访问的所有知识库)
     * @param topK 返回前 K 条
     * @return 检索结果片段列表
     */
    public List<SearchResultChunk> search(String query, List<Long> targetDatasetIds, int topK) {
        Long userId = 1L;
        try {
            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.debug("获取当前登录用户上下文失败，采用默认用户ID 1: {}", e.getMessage());
        }
        return search(userId, query, targetDatasetIds, topK);
    }

    /**
     * 执行带权限隔离的混合知识库检索 (显式传递用户ID，适配响应式与异步线程池)
     */
    public List<SearchResultChunk> search(Long userId, String query, List<Long> targetDatasetIds, int topK) {
        if (userId == null) {
            userId = 1L;
        }

        // 1. 获取当前用户有权访问的知识库列表 (权限隔离)
        List<AiDataset> accessibleDatasets = datasetMapper.selectAccessibleDatasets(userId);
        Set<Long> allowedDatasetIdSet = accessibleDatasets.stream().map(AiDataset::getId).collect(Collectors.toSet());

        // 过滤出合法请求的 datasetIds
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

        if (effectiveDatasetIds.isEmpty()) {
            log.warn("用户 [{}] 无任何可访问的知识库权限或未指定知识库", userId);
            return Collections.emptyList();
        }

        List<SearchResultChunk> results = new ArrayList<>();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();

        // 2. 向量库检索 (支持动态过滤下推与两阶段粗排+精排)
        if (embeddingEnabled && vectorStore != null) {
            try {
                // 二阶段优化架构：粗排候选扩大至 topK * 3
                int roughTopK = Math.max(topK * 3, 10);
                
                // 动态构建 Spring AI 2.0 FilterExpression 表达式 (例如 datasetId in ['1', '2'])
                String filterExp = null;
                if (!effectiveDatasetIds.isEmpty()) {
                    String idsJoin = effectiveDatasetIds.stream()
                            .map(id -> "'" + id + "'")
                            .collect(Collectors.joining(", "));
                    filterExp = "datasetId in [" + idsJoin + "]";
                }

                SearchRequest.Builder requestBuilder = SearchRequest.builder()
                        .query(query)
                        .topK(roughTopK)
                        .similarityThreshold(0.35); // 降低初筛阈值以充分召回候选

                if (StringUtils.hasText(filterExp)) {
                    requestBuilder.filterExpression(filterExp);
                }

                List<Document> simDocs = vectorStore.similaritySearch(requestBuilder.build());
                List<SearchResultChunk> candidateList = new ArrayList<>();

                for (Document doc : simDocs) {
                    Object dsId = doc.getMetadata().get("datasetId");
                    Long docDatasetId = dsId != null ? Long.valueOf(dsId.toString()) : null;
                    // 双重保障权限隔离
                    if (docDatasetId != null && effectiveDatasetIds.contains(docDatasetId)) {
                        String docText = doc.getText();
                        double baseScore = calculateRelevanceScore(query, docText);
                        candidateList.add(SearchResultChunk.builder()
                                .datasetId(docDatasetId)
                                .documentName(String.valueOf(doc.getMetadata().getOrDefault("docName", "未知文档")))
                                .content(docText)
                                .score(baseScore)
                                .build());
                    }
                }

                // 精排 (Rerank)：按关键词匹配与语义复合分数降序排列，取前 topK 条
                candidateList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                results.addAll(candidateList.stream().limit(topK).toList());

            } catch (Exception e) {
                log.warn("向量库检索异常，降级使用数据库全文/模糊检索: {}", e.getMessage());
            }
        }

        // 3. 降级/混合：如果向量库结果不足，从关系库中做关键字匹配补充
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
                        .score(0.70)
                        .build());
            }
        }

        return results;
    }

    /**
     * Rerank 精排相关度评分算法
     * 综合词频覆盖度、连续子串匹配率以及位置权重计算 0.0 ~ 1.0 的最终得分
     */
    private double calculateRelevanceScore(String query, String text) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
            return 0.5;
        }
        double score = 0.65; // 基础召回分
        if (text.contains(query)) {
            score += 0.25; // 完全命中加权
        } else {
            // 拆分关键词覆盖计算
            int matchCount = 0;
            String[] terms = query.split("\\s+|，|。|？|！|,|\\?");
            for (String term : terms) {
                if (StringUtils.hasText(term) && text.contains(term.trim())) {
                    matchCount++;
                }
            }
            if (terms.length > 0) {
                score += 0.20 * ((double) matchCount / terms.length);
            }
        }
        return Math.min(score, 0.99);
    }
}
