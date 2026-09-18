package com.ragagent.memory.service;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 短期工作记忆服务 (Short-term Working Memory)
 * 纯内存驱动 (Redis 10号库)，支持会话多轮滑动窗口、即时会话上下文、Scratchpad 临时黑板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortTermMemoryService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX_HISTORY = "rag:agent:short_term:history:";
    private static final String KEY_PREFIX_SCRATCHPAD = "rag:agent:short_term:scratchpad:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final int DEFAULT_WINDOW_SIZE = 10;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortTermMessage implements Serializable {
        private String role; // "user" | "assistant" | "system"
        private String content;
        private String timestamp;
    }

    /**
     * 追加单条消息到短期记忆滑动窗口
     */
    public void appendMessage(String sessionId, String role, String content) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            return;
        }
        String key = KEY_PREFIX_HISTORY + sessionId;
        ShortTermMessage msg = ShortTermMessage.builder()
                .role(role)
                .content(content)
                .timestamp(LocalDateTime.now().toString())
                .build();
        try {
            redisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(msg));
            // 维持滑动窗口大小
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > DEFAULT_WINDOW_SIZE) {
                redisTemplate.opsForList().trim(key, size - DEFAULT_WINDOW_SIZE, -1);
            }
            redisTemplate.expire(key, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("保存短期对话记忆异常: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取指定会话的短期滑动窗口历史
     */
    public List<ShortTermMessage> getRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId)) {
            return Collections.emptyList();
        }
        String key = KEY_PREFIX_HISTORY + sessionId;
        try {
            int fetchCount = limit > 0 ? limit : DEFAULT_WINDOW_SIZE;
            List<String> rawList = redisTemplate.opsForList().range(key, -fetchCount, -1);
            if (rawList == null || rawList.isEmpty()) {
                return Collections.emptyList();
            }
            List<ShortTermMessage> messages = new ArrayList<>();
            for (String item : rawList) {
                messages.add(JSONUtil.toBean(item, ShortTermMessage.class));
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取短期对话记忆异常: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 格式化短期记忆为对话 Prompt 文本片段
     */
    public String getFormattedRecentHistory(String sessionId, int limit) {
        List<ShortTermMessage> list = getRecentMessages(sessionId, limit);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【近期会话上下文记忆】:\n");
        for (ShortTermMessage msg : list) {
            String roleName = "assistant".equalsIgnoreCase(msg.getRole()) ? "助手" : "用户";
            sb.append(roleName).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 设置 Scratchpad 临时黑板变量
     */
    public void setScratchpadValue(String sessionId, String field, String value) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(field)) {
            return;
        }
        String key = KEY_PREFIX_SCRATCHPAD + sessionId;
        redisTemplate.opsForHash().put(key, field, value);
        redisTemplate.expire(key, DEFAULT_TTL);
    }

    /**
     * 获取 Scratchpad 临时黑板变量
     */
    public String getScratchpadValue(String sessionId, String field) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(field)) {
            return null;
        }
        String key = KEY_PREFIX_SCRATCHPAD + sessionId;
        Object val = redisTemplate.opsForHash().get(key, field);
        return val != null ? val.toString() : null;
    }

    /**
     * 清理会话的所有短期记忆与临时黑板
     */
    public void clearSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX_HISTORY + sessionId);
        redisTemplate.delete(KEY_PREFIX_SCRATCHPAD + sessionId);
    }
}
