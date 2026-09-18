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
 * <h1>长期记忆与用户画像中枢服务 (Long-Term Episodic & Semantic Memory)</h1>
 * <p>
 * 本服务深度实现了著名论文 <b>《Generative Agents: Interactive Simulacra of Human Behavior》（斯坦福虚拟小镇）</b> 
 * 与 <b>Mem0</b> 架构中的长期记忆检索与神经强化机制。
 * <p>
 * <b>核心学习知识点与算法数学原理：</b>
 * <ol>
 *   <li><b>三维加权检索得分模型 (Memory Scoring Function)：</b>
 *       <br>单靠语义相似度无法反映人脑的记忆规律。综合公式定义为：
 *       <br>{@code FinalScore = 0.60 * Relevance(语义余弦相似度) + 0.25 * Importance(重要度权重) + 0.15 * Recency(艾宾浩斯时间衰减)}
 *       <br>• <b>Relevance</b>：通过 Embedding 向量衡量与当前提问的相关性。
 *       <br>• <b>Importance</b>：由反思模型评定（1~10），核心习惯/制度规范给予高分。
 *       <br>• <b>Recency</b>：距离上次回忆的时间越近衰减越少，越久远衰减越多。
 *   </li>
 *   <li><b>检索强化学习回路 (Reinforcement on Retrieval)：</b>
 *       <br>每当某条记忆被成功召回并注入当前提示词后，后台异步自增其 {@code accessCount} 并将 {@code lastAccessedTime} 刷新为当前时间，
 *       模拟大脑神经元“越常提取的记忆越根深蒂固”的生理特征。</li>
 *   <li><b>pgvector 高维余弦计算与弹性降级：</b>
 *       <br>优先走数据库内建的 HNSW / IVFFlat 向量索引计算，若未开启 Embedding 或超时则安全平滑降级至关键词与重要度复合检索。</li>
 * </ol>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    /** 长期记忆持久层 Mapper (包含自定义 pgvector 混合排序 SQL) */
    private final AiAgentMemoryMapper memoryMapper;
    /** Spring AI 嵌入模型提供者 (按需装配) */
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    /** 是否开启远程 Embedding 模型 */
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;
    /** 向量调用超时时间 (秒) */
    @org.springframework.beans.factory.annotation.Value("${EMBEDDING_TIMEOUT_SEC:120}")
    private int embeddingTimeoutSec;

    /**
     * <h3>混合加权检索长期记忆 (Stanford Generative Agents 算法)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li>向量化提问词后，通过自定义 SQL 直接在 PostgreSQL pgvector 中完成：
     *       {@code (1 - (embedding <=> query_vector)) * 0.6 + (importance / 10.0) * 0.25 + exp(-0.01 * extract(epoch from (now() - last_accessed_time)) / 3600) * 0.15}
     *   </li>
     *   <li>召回后并发触发异步强化（Reinforcement），完全不阻塞用户正常的对话响应时间。</li>
     * </ul>
     *
     * @param userId 发起用户 ID
     * @param query  用户输入的问题文本
     * @param topK   期望召回的最高置信记忆条数
     * @return 排序后的长期记忆列表
     */
    public List<AiAgentMemory> searchMemories(Long userId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        // 路径一：启用 pgvector 向量加速与三维复合加权检索
        if (embeddingEnabled) {
            EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel != null) {
                try {
                    // 设置超时保护，避免网络抖动挂起当前线程
                    float[] vector = CompletableFuture.supplyAsync(() -> embeddingModel.embed(query))
                            .get(Math.max(embeddingTimeoutSec, 10), java.util.concurrent.TimeUnit.SECONDS);
                    String vectorStr = toPgVectorString(vector);
                    List<AiAgentMemory> list = memoryMapper.searchHybridMemories(userId, vectorStr, topK);

                    // 异步触发记忆强化机制 (Reinforcement Learning from Memory Retrieval)
                    if (!list.isEmpty()) {
                        CompletableFuture.runAsync(() -> {
                            for (AiAgentMemory mem : list) {
                                memoryMapper.reinforceMemory(mem.getId());
                            }
                        });
                        return list;
                    }
                } catch (Exception e) {
                    log.warn("【LongTermMemory】pgvector 混合检索失败或超时，自动平滑降级为关系库查询: error={}", e.getMessage());
                }
            }
        }

        // 路径二：降级兜底查询 —— 按关键词重合度、重要度与访问时间排序
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
     * <h3>新增长期记忆并异步构建向量索引</h3>
     *
     * @param userId     所属用户 ID
     * @param agentId    关联智能体 ID (可为空)
     * @param sessionId  来源会话 ID
     * @param memoryType 记忆分类：SEMANTIC(事实知识)、PREFERENCE(操作偏好)、EPISODIC(情景对话)、CORRECTION(纠错准则)
     * @param content    记忆文本内容
     * @param importance 重要度评分 (1.0 ~ 10.0，默认 5.0)
     * @param metadata   扩展 JSON 属性
     * @return 持久化后的记忆实体
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
