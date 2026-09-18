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

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final AiGraphEntityMapper entityMapper;
    private final AiGraphRelationMapper relationMapper;

    public record SubgraphResult(List<AiGraphEntity> entities, List<AiGraphRelation> relations) {}

    /**
     * 根据关键词检索相关实体并向外扩展 1 度拓扑网络
     */
    public SubgraphResult expandSubgraph(String keyword, List<Long> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return new SubgraphResult(Collections.emptyList(), Collections.emptyList());
        }

        // 1. 查找匹配关键词的实体
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

        // 2. 查出一阶关系三元组
        List<AiGraphRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<AiGraphRelation>()
                .in(AiGraphRelation::getDatasetId, datasetIds)
                .and(w -> w.in(AiGraphRelation::getSourceEntity, entityNames).or().in(AiGraphRelation::getTargetEntity, entityNames))
                .last("LIMIT 20"));

        return new SubgraphResult(matchedEntities, relations);
    }
}
