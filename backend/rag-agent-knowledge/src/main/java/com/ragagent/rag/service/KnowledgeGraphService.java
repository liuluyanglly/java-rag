package com.ragagent.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.rag.entity.AiGraphEntity;
import com.ragagent.rag.entity.AiGraphRelation;
import com.ragagent.rag.mapper.AiGraphEntityMapper;
import com.ragagent.rag.mapper.AiGraphRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * <h1>知识图谱拓扑服务 (GraphRAG Subgraph Service)</h1>
 * <p>
 * 本服务是企业级 <b>GraphRAG (基于知识图谱的检索增强生成)</b> 的核心组成部分。
 * <p>
 * <b>核心学习知识点：</b>
 * <ol>
 *   <li><b>为什么需要图谱检索（GraphRAG vs Vector RAG）？</b>
 *       <br>向量检索（Dense Retrieval）只能找到“语义相近”的孤立文本块，当用户提出需要跨文档因果推演（如“A 系统故障会导致哪些下游系统受到什么影响？”）时，
 *       纯向量切片常常遗漏关键因果链。知识图谱以【实体-关系-实体】（三元组）为网络骨架，能够通过拓扑图遍历还原完整因果关系网。</li>
 *   <li><b>子图扩展机制 (Subgraph Expansion)：</b>
 *       <br>从 Query 中提取的核心实体出发，向外扩散检索 1 度（Direct Hop）或多度邻接关系，构建微型局部知识子图注入大模型上下文。</li>
 * </ol>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    /** 实体元数据持久层 Mapper */
    private final AiGraphEntityMapper entityMapper;
    /** 三元组关系边持久层 Mapper */
    private final AiGraphRelationMapper relationMapper;

    /**
     * 拓扑子图抽取结果封装 (Entities + Relations)
     */
    public record SubgraphResult(List<AiGraphEntity> entities, List<AiGraphRelation> relations) {}

    /**
     * <h3>根据查询关键词定位种子实体并向外扩展 1 度拓扑关系网络</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li>步骤 1：基于租户知识库权限，模糊匹配定位前 Top-5 核心种子实体（Seed Entities）。</li>
     *   <li>步骤 2：以这些实体为起点（Source）或终点（Target），批量检索前 20 条相关联的一阶关系边（Relations），拼装为结构化三元组注入 RAG 上下文。</li>
     * </ul>
     *
     * @param keyword    用户提问关键词或核心技术名词
     * @param datasetIds 授权检索的目标知识库 ID 列表
     * @return 包含命中实体与关系边的子图集合
     */
    public SubgraphResult expandSubgraph(String keyword, List<Long> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty() || keyword == null) {
            return new SubgraphResult(Collections.emptyList(), Collections.emptyList());
        }

        // 步骤 1：查找匹配关键词的种子实体 (Seed Entities)
        List<AiGraphEntity> matchedEntities = entityMapper.selectList(new LambdaQueryWrapper<AiGraphEntity>()
                .in(AiGraphEntity::getDatasetId, datasetIds)
                .like(AiGraphEntity::getEntityName, keyword.length() > 4 ? keyword.substring(0, 4) : keyword)
                .last("LIMIT 5"));

        if (matchedEntities.isEmpty()) {
            return new SubgraphResult(Collections.emptyList(), Collections.emptyList());
        }

        Set<String> entityNames = new HashSet<>();
        for (AiGraphEntity e : matchedEntities) {
            entityNames.add(e.getEntityName());
        }

        // 步骤 2：向外扩展查出一阶关联的三元组拓扑边 (Source -[Relation]-> Target)
        List<AiGraphRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<AiGraphRelation>()
                .in(AiGraphRelation::getDatasetId, datasetIds)
                .and(w -> w.in(AiGraphRelation::getSourceEntity, entityNames).or().in(AiGraphRelation::getTargetEntity, entityNames))
                .last("LIMIT 20"));

        return new SubgraphResult(matchedEntities, relations);
    }
}
