package com.ragagent.memory.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ragagent.memory.dto.MemoryDecisionResult;
import com.ragagent.memory.dto.ReflexionResult;
import com.ragagent.memory.entity.AiAgentMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆自进化服务 (Memory Evolution & Reflexion Engine)
 * 包含：
 * 1. 对话自主提炼与事实更新 (基于 Mem0 架构)
 * 2. 用户批评与纠错的自省闭环 (基于 Reflexion 论文框架，提炼防再犯规约)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEvolutionService {

    private final MemoryDecisionEngine decisionEngine;
    private final LongTermMemoryService longTermMemoryService;
    private final ObjectProvider<ChatModel> chatModelProvider;

    private static final String REFLEXION_SYSTEM_PROMPT = """
            你是一个具备深度反省与自愈能力的自我进化诊断专家（基于 Reflexion 自省框架）。
            当 Agent 的回答遭到用户的指责、纠错或负反馈时，你需要深入分析错误成因，并输出能够指导未来行为的严格规范。
            
            请严格以 JSON 格式输出反思结果:
            {
              "errorDiagnosis": "详细分析 Agent 回答产生缺陷的根本原因（如事实错误、忽略限制、版本错位等）",
              "improvementRule": "面向未来生成的极简且绝对遵从的行为与知识准则（例如：'在编写 SQL 向量转换时，严禁使用直接赋值，必须使用 CAST(? AS vector) 显式强转'）",
              "importance": 9.5
            }
            """;

    /**
     * 异步从日常对话中提炼进化（Mem0 被动自主进化）
     */
    public void evolveFromConversationAsync(Long userId, Long agentId, String sessionId, String userMsg, String assistantResp) {
        CompletableFuture.runAsync(() -> {
            try {
                List<MemoryDecisionResult> decisions = decisionEngine.analyzeAndDecide(userId, userMsg, assistantResp);
                for (MemoryDecisionResult decision : decisions) {
                    applyDecision(userId, agentId, sessionId, decision);
                }
                if (!decisions.isEmpty()) {
                    log.info("会话 [{}] 成功触发记忆自进化，应用了 {} 项决策", sessionId, decisions.size());
                }
            } catch (Exception e) {
                log.error("会话自进化提炼异常: sessionId={}, error={}", sessionId, e.getMessage());
            }
        });
    }

    /**
     * Reflexion 主动纠错自省与自我进化（当用户点踩或提交批注指正时调用）
     */
    public ReflexionResult reflectAndEvolve(Long userId, Long agentId, String sessionId,
                                           String query, String originalAnswer, String userFeedback) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null || !StringUtils.hasText(query)) {
            return null;
        }

        try {
            StringBuilder input = new StringBuilder();
            input.append("【用户提问】: ").append(query).append("\n")
                 .append("【Agent 原始回答】: ").append(originalAnswer != null ? originalAnswer : "(无回答)").append("\n")
                 .append("【用户批评与纠错指出】: ").append(userFeedback).append("\n");

            String reflexionJson = ChatClient.create(chatModel)
                    .prompt()
                    .system(REFLEXION_SYSTEM_PROMPT)
                    .user(input.toString())
                    .call()
                    .content();

            ReflexionResult result = parseReflexion(reflexionJson);
            if (result != null) {
                result.setQuery(query);
                result.setOriginalAnswer(originalAnswer);
                result.setUserFeedback(userFeedback);

                // 沉淀为 CORRECTION 长期自进化纠错记忆，设置极高重要度
                String metadata = JSONUtil.createObj()
                        .set("source", "REFLEXION_FEEDBACK")
                        .set("userFeedback", userFeedback)
                        .set("errorDiagnosis", result.getErrorDiagnosis())
                        .toString();

                AiAgentMemory saved = longTermMemoryService.addMemory(
                        userId,
                        agentId,
                        sessionId,
                        "CORRECTION",
                        "【纠错自省准则】" + result.getImprovementRule(),
                        result.getImportance() != null ? result.getImportance() : 9.5,
                        metadata
                );

                log.info("Agent 完成自我反思与进化！已沉淀纠错规约记忆: id={}, rule={}",
                        saved != null ? saved.getId() : "null", result.getImprovementRule());
            }
            return result;
        } catch (Exception e) {
            log.error("Reflexion 反思进化执行异常", e);
            return null;
        }
    }

    /**
     * 将决策执行到长期数据库
     */
    private void applyDecision(Long userId, Long agentId, String sessionId, MemoryDecisionResult decision) {
        String action = decision.getAction();
        if ("ADD".equalsIgnoreCase(action)) {
            longTermMemoryService.addMemory(
                    userId,
                    agentId,
                    sessionId,
                    decision.getMemoryType(),
                    decision.getContent(),
                    decision.getImportance(),
                    JSONUtil.createObj().set("rationale", decision.getRationale()).toString()
            );
        } else if ("UPDATE".equalsIgnoreCase(action) && StringUtils.hasText(decision.getTargetMemoryId())) {
            longTermMemoryService.updateMemory(
                    decision.getTargetMemoryId(),
                    decision.getContent(),
                    decision.getImportance()
            );
        } else if ("DELETE".equalsIgnoreCase(action) && StringUtils.hasText(decision.getTargetMemoryId())) {
            longTermMemoryService.deleteMemory(decision.getTargetMemoryId());
        }
    }

    private ReflexionResult parseReflexion(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String clean = cleanJson(raw);
            JSONObject obj = JSONUtil.parseObj(clean);
            return ReflexionResult.builder()
                    .errorDiagnosis(obj.getStr("errorDiagnosis"))
                    .improvementRule(obj.getStr("improvementRule"))
                    .importance(obj.getDouble("importance", 9.5))
                    .build();
        } catch (Exception e) {
            log.error("解析反思结果 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private String cleanJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
