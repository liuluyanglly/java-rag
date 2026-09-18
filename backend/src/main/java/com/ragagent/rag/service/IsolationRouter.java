package com.ragagent.rag.service;

import com.ragagent.rag.entity.AiDataset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 双轨隔离路由器 (IsolationRouter)
 * 根据知识库的配置判定并路由至“逻辑元数据过滤”或“物理专属 Schema / 私有化实例”
 */
@Slf4j
@Component
public class IsolationRouter {

    public enum IsolationMode {
        LOGICAL_SHARED,      // 共享表 + 元数据过滤
        PHYSICAL_ISOLATED    // 专属 Schema / 独立物理库 + 私有本地模型
    }

    public record RoutingStrategy(
            IsolationMode mode,
            String targetSchema,
            String vectorTableName,
            boolean enforceLocalModel
    ) {}

    /**
     * 路由决策计算
     */
    public RoutingStrategy determineStrategy(AiDataset dataset) {
        if (dataset != null && "PHYSICAL".equalsIgnoreCase(dataset.getIsolationType())) {
            String schema = dataset.getIsolatedSchema() != null ? dataset.getIsolatedSchema() : "isolated_sec_default";
            log.info("知识库 [{}] 判定为【物理隔离模式】，路由至专属空间: Schema={}, 强制内网离线模型", dataset.getName(), schema);
            return new RoutingStrategy(
                    IsolationMode.PHYSICAL_ISOLATED,
                    schema,
                    schema + ".ai_document_chunk",
                    true // 物理隔离强制要求数据不得上传公网模型
            );
        }

        return new RoutingStrategy(
                IsolationMode.LOGICAL_SHARED,
                "public",
                "ai_document_chunk",
                false
        );
    }
}
