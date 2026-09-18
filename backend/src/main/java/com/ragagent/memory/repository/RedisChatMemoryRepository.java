package com.ragagent.memory.repository;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Spring AI 2.0 原生 ChatMemoryRepository 的 Redis 持久化实现
 * 专为企业对话设计，数据落入 Redis 10 号数据库，支持 TTL 自动过期与按 conversationId 隔离
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;

    public static final String KEY_PREFIX = "rag:agent:chat_memory:";
    public static final String INDEX_SET_KEY = "rag:agent:chat_memory:conversations";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7); // 7天过期
    private static final int MAX_MESSAGES_LIMIT = 50;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializableMessage implements Serializable {
        private String messageType; // USER, ASSISTANT, SYSTEM
        private String text;
        private Map<String, Object> metadata;
        private long timestamp;
    }

    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(INDEX_SET_KEY);
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(members);
        } catch (Exception e) {
            log.error("获取会话列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Collections.emptyList();
        }
        String key = KEY_PREFIX + conversationId;
        try {
            List<String> rawJsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (rawJsonList == null || rawJsonList.isEmpty()) {
                return Collections.emptyList();
            }

            List<Message> messages = new ArrayList<>();
            for (String raw : rawJsonList) {
                SerializableMessage sm = JSONUtil.toBean(raw, SerializableMessage.class);
                Message msg = deserializeMessage(sm);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            return messages;
        } catch (Exception e) {
            log.error("从 Redis 读取会话记忆失败: conversationId={}, error={}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + conversationId;
        try {
            // 全量刷新或保存新列表
            List<String> jsonList = new ArrayList<>();
            for (Message m : messages) {
                if (m == null) continue;
                SerializableMessage sm = SerializableMessage.builder()
                        .messageType(m.getMessageType() != null ? m.getMessageType().name() : MessageType.USER.name())
                        .text(m.getText())
                        .metadata(m.getMetadata())
                        .timestamp(Instant.now().toEpochMilli())
                        .build();
                jsonList.add(JSONUtil.toJsonStr(sm));
            }

            // 使用原子覆盖
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            
            // 维持最大容量裁剪
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MAX_MESSAGES_LIMIT) {
                redisTemplate.opsForList().trim(key, size - MAX_MESSAGES_LIMIT, -1);
            }

            // 续期 TTL
            redisTemplate.expire(key, DEFAULT_TTL);
            // 记录索引
            redisTemplate.opsForSet().add(INDEX_SET_KEY, conversationId);
            redisTemplate.expire(INDEX_SET_KEY, DEFAULT_TTL);
        } catch (Exception e) {
            log.error("向 Redis 保存会话记忆失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        String key = KEY_PREFIX + conversationId;
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(INDEX_SET_KEY, conversationId);
            log.info("已成功清理 Redis 中会话记忆: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("清理 Redis 会话记忆失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    private Message deserializeMessage(SerializableMessage sm) {
        if (sm == null || !StringUtils.hasText(sm.getText())) {
            return null;
        }
        String type = sm.getMessageType();
        if (MessageType.USER.name().equalsIgnoreCase(type)) {
            return new UserMessage(sm.getText());
        } else if (MessageType.ASSISTANT.name().equalsIgnoreCase(type)) {
            return new AssistantMessage(sm.getText());
        } else if (MessageType.SYSTEM.name().equalsIgnoreCase(type)) {
            return new SystemMessage(sm.getText());
        } else {
            return new UserMessage(sm.getText());
        }
    }
}
