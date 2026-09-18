package com.ragagent.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 2.0 ChatClient 核心装配配置
 * 统一集成：
 * 1. SimpleLoggerAdvisor 日志拦截器（自动审计请求与流式响应）
 * 2. MessageChatMemoryAdvisor 记忆拦截器（自动绑定 Redis 10号库会话滑动窗口）
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder(
            ObjectProvider<ChatModel> chatModelProvider,
            ChatMemory chatMemory) {
        
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        ChatClient.Builder builder = (chatModel != null)
                ? ChatClient.builder(chatModel)
                : ChatClient.builder(prompt -> { throw new IllegalStateException("ChatModel 未配置"); });

        log.info("装配 Spring AI 2.0 ChatClient.Builder: 挂载 SimpleLoggerAdvisor 日志拦截与 Redis MessageChatMemoryAdvisor 短期记忆");
        return builder.defaultAdvisors(
                new SimpleLoggerAdvisor(),
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        );
    }

    @Bean
    public ChatClient defaultChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
