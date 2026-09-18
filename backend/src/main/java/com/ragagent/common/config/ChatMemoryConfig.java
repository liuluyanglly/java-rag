package com.ragagent.common.config;

import com.ragagent.memory.repository.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 2.0 对话记忆与滑动窗口装配配置
 * 统一将多轮短期对话记忆持久化至 Redis (10号库)
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

    private static final int DEFAULT_WINDOW_SIZE = 20; // 滑动窗口包含最多 20 条消息（约 10 轮对话）

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        log.info("初始化 Spring AI 2.0 MessageWindowChatMemory, 基于 Redis 10号库, 滑动窗口大小: {}", DEFAULT_WINDOW_SIZE);
        return MessageWindowChatMemory.builder()
                .maxMessages(DEFAULT_WINDOW_SIZE)
                .chatMemoryRepository(redisChatMemoryRepository)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
