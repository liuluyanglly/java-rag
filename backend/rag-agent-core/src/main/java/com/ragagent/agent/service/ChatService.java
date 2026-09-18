package com.ragagent.agent.service;

import com.ragagent.agent.dto.ChatRequest;
import com.ragagent.agent.entity.AiChatMessage;
import com.ragagent.agent.entity.AiChatSession;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能体对话中枢业务服务接口
 */
public interface ChatService {

    /**
     * 响应式流式对话交互（SSE）
     */
    Flux<ServerSentEvent<String>> streamChat(ChatRequest request, Long userId);

    /**
     * 获取用户所有历史会话
     */
    List<AiChatSession> listSessions(Long userId);

    /**
     * 获取指定会话的历史消息明细
     */
    List<AiChatMessage> getSessionMessages(String sessionId);

    /**
     * 删除会话及其所有关联消息
     */
    void deleteSession(String sessionId);

    /**
     * 手动更新会话标题
     */
    void updateSessionTitle(String sessionId, String title);
}
