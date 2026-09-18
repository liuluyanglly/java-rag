package com.ragagent.agent.controller;

import com.ragagent.agent.dto.ChatRequest;
import com.ragagent.agent.entity.AiChatMessage;
import com.ragagent.agent.entity.AiChatSession;
import com.ragagent.agent.service.ChatService;
import com.ragagent.common.result.Result;
import com.ragagent.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent 对话核心控制中枢 (Reactive Streaming Chat Controller)
 * 遵循多模块规范与轻量 Controller 原则，所有业务逻辑全部下沉至 ChatService 业务实现类。
 */
@Slf4j
@Tag(name = "Agent 流式对话与会话中心")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "获取当前用户的所有会话列表",
            description = "置顶会话优先，其余按最后活跃时间倒序，包含最新问答摘要与消息数量")
    @GetMapping("/sessions")
    public Result<List<AiChatSession>> getSessions(ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        return Result.success(chatService.listSessions(userId));
    }

    @Operation(summary = "创建新会话",
            description = "显式新建空会话并返回 sessionId。若最新会话无历史消息，则复用该空会话，防止产生重复空对话")
    @PostMapping("/session")
    public Result<AiChatSession> createSession(
            @Parameter(description = "绑定的智能体ID，可为空", example = "1")
            @RequestParam(name = "agentId", required = false) Long agentId,
            ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        List<AiChatSession> sessions = chatService.listSessions(userId);
        if (!sessions.isEmpty()) {
            AiChatSession latest = sessions.get(0);
            if (latest.getMessageCount() != null && latest.getMessageCount() == 0) {
                return Result.success(latest);
            }
        }
        String newSessionId = cn.hutool.core.util.IdUtil.simpleUUID();
        chatService.updateSessionTitle(newSessionId, "新对话");
        AiChatSession session = AiChatSession.builder()
                .sessionId(newSessionId)
                .userId(userId)
                .agentId(agentId)
                .title("新对话")
                .pinned(false)
                .createTime(java.time.LocalDateTime.now())
                .updateTime(java.time.LocalDateTime.now())
                .build();
        return Result.success(session);
    }

    @Operation(summary = "获取会话历史消息记录",
            description = "按消息生成时间升序返回该会话的全部多轮消息")
    @GetMapping("/history/{sessionId}")
    public Result<List<AiChatMessage>> getHistory(
            @Parameter(description = "会话UUID", example = "3f2a9c1b7e4d4a10", required = true)
            @PathVariable String sessionId) {
        return Result.success(chatService.getSessionMessages(sessionId));
    }

    @Operation(summary = "删除指定对话会话", description = "级联清理会话记录、历史消息以及Redis短期记忆缓存")
    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> deleteSession(
            @Parameter(description = "会话UUID", example = "3f2a9c1b7e4d4a10", required = true)
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        chatService.deleteSession(sessionId);
        return Result.success(true);
    }

    @Operation(summary = "重命名会话标题")
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @Parameter(description = "会话UUID", example = "3f2a9c1b7e4d4a10", required = true)
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String title = body != null ? body.get("title") : "新对话";
        chatService.updateSessionTitle(sessionId, title);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "SSE 流式智能对话 (集成意图识别 + 宿主机真实时间感知 + 记忆演化 + RAG知识检索 + 智能标题生成)",
            description = "以 text/event-stream 持续推送 thought, memories, citations, message, finish 等事件")
    @ApiResponse(responseCode = "200", description = "SSE 事件流持续推送直至 finish 事件",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string", description = "单个 SSE 事件的 data 载荷")))
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest req, ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        return chatService.streamChat(req, userId);
    }
}
