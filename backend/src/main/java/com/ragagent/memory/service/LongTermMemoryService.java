package com.ragagent.memory.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.memory.entity.AiAgentMemory;
import com.ragagent.memory.mapper.AiAgentMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 长期记忆服务 (Long-term Episodic & Semantic Memory)
 * 基于 pgvector 向量计算 + 重要度 + 艾宾浩斯时间遗忘衰减(Stanford Generative Agents 算法)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final AiAgentMemoryMapper memoryMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;
    @org.springframework.beans.factory.annotation.Value("${EMBEDDING_TIMEOUT_SEC:120}")
    private int embeddingTimeoutSec;

    /**
     * 混合加权检索长期记忆
     * 综合分值 = 60% 余弦语义相似度 + 25% 重要度权重 + 15% 艾宾浩斯时间遗忘曲线
     */
    public List<AiAgentMemory> searchMemories(Long userId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        if (embeddingEnabled) {
            EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel != null) {
            try {
                // 设置超时保护，优先遵循系统统一配置的 embedding 超时
                float[] vector = CompletableFuture.supplyAsync(() -> embeddingModel.embed(query))
                        .get(Math.max(embeddingTimeoutSec, 10), java.util.concurrent.TimeUnit.SECONDS);
                String vectorStr = toPgVectorString(vector);
                List<AiAgentMemory> list = memoryMapper.searchHybridMemories(userId, vectorStr, topK);

                // 异步进行记忆强化 (Reinforcement Learning from Memory Retrieval)
                if (!list.isEmpty()) {
                    CompletableFuture.runAsync(() -> {
                        for (AiAgentMemory mem : list) {
                            memoryMapper.reinforceMemory(mem.getId());
                        }
                    });
                    return list;
                }
            } catch (Exception e) {
                log.warn("pgvector 混合检索失败或超时，自动平滑降级: error={}", e.getMessage());
            }
            }
        }

        // 降级高可用查询：按关键词相关度与重要度混合加权
        List<AiAgentMemory> allMemories = memoryMapper.selectList(new LambdaQueryWrapper<AiAgentMemory>()
                .eq(AiAgentMemory::getUserId, userId)
                .orderByDesc(AiAgentMemory::getImportance)
                .orderByDesc(AiAgentMemory::getLastAccessedTime));
        if (allMemories.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiAgentMemory> matched = allMemories.stream()
                .filter(m -> m.getContent() != null && (m.getContent().contains(query) || query.contains(m.getContent().substring(0, Math.min(4, m.getContent().length())))))
                .limit(topK)
                .toList();

        List<AiAgentMemory> result = matched.isEmpty() ? allMemories.stream().limit(topK).toList() : matched;
        CompletableFuture.runAsync(() -> {
            for (AiAgentMemory mem : result) {
                memoryMapper.reinforceMemory(mem.getId());
            }
        });
        return result;
    }

    /**
     * 新增长期记忆
     */
    public AiAgentMemory addMemory(Long userId, Long agentId, String sessionId,
                                  String memoryType, String content, Double importance, String metadata) {
        if (userId == null || !StringUtils.hasText(content)) {
            return null;
        }

        AiAgentMemory memory = AiAgentMemory.builder()
                .id(IdUtil.fastSimpleUUID())
                .userId(userId)
                .agentId(agentId)
                .sessionId(sessionId)
                .memoryType(StringUtils.hasText(memoryType) ? memoryType : "SEMANTIC")
                .content(content.trim())
                .importance(importance != null ? importance : 5.0)
                .accessCount(1)
                .metadata(metadata)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .lastAccessedTime(LocalDateTime.now())
                .build();

        memoryMapper.insert(memory);

        // 异步计算向量并写入数据库
        syncEmbeddingAsync(memory.getId(), memory.getContent());

        return memory;
    }

    /**
     * 更新既有记忆内容及重要度
     */
    public boolean updateMemory(String id, String newContent, Double importance) {
        AiAgentMemory existing = memoryMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        if (StringUtils.hasText(newContent)) {
            existing.setContent(newContent.trim());
        }
        if (importance != null) {
            existing.setImportance(importance);
        }
        existing.setUpdateTime(LocalDateTime.now());
        memoryMapper.updateById(existing);

        if (StringUtils.hasText(newContent)) {
            syncEmbeddingAsync(id, newContent.trim());
        }
        return true;
    }

    /**
     * 删除指定记忆
     */
    public boolean deleteMemory(String id) {
        return memoryMapper.deleteById(id) > 0;
    }

    /**
     * 获取指定用户的所有记忆画像
     */
    public List<AiAgentMemory> getUserMemories(Long userId, String memoryType) {
        LambdaQueryWrapper<AiAgentMemory> query = new LambdaQueryWrapper<AiAgentMemory>()
                .eq(AiAgentMemory::getUserId, userId);
        if (StringUtils.hasText(memoryType)) {
            query.eq(AiAgentMemory::getMemoryType, memoryType);
        }
        query.orderByDesc(AiAgentMemory::getLastAccessedTime);
        return memoryMapper.selectList(query);
    }

    /**
     * 异步更新记忆向量
     */
    private void syncEmbeddingAsync(String id, String content) {
        if (embeddingEnabled) {
            EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        float[] vector = embeddingModel.embed(content);
                        String vectorStr = toPgVectorString(vector);
                        memoryMapper.updateEmbedding(id, vectorStr);
                    } catch (Exception e) {
                        log.error("生成记忆向量并保存失败: id={}, error={}", id, e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * 将 float[] 转换为 PostgreSQL vector 格式: [0.12, -0.34, ...]
     */
    private String toPgVectorString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
