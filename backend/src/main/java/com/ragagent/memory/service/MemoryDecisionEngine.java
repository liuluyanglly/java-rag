package com.ragagent.memory.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ragagent.memory.dto.MemoryDecisionResult;
import com.ragagent.memory.entity.AiAgentMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mem0 记忆决策引擎
 * 负责从交互中智能抽取事实/偏好，比对既有记忆并做出 ADD / UPDATE / DELETE / NOOP 的冲突消解与生命周期决策
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryDecisionEngine {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final LongTermMemoryService longTermMemoryService;

    private static final String EXTRACT_PROMPT = """
            你是一个专业的个人与企业长期记忆提炼助手。
            请分析以下对话，提取其中关于用户的关键信息（长期有价值的事实、技术栈、使用偏好、习惯或明确约束）。
            
            【注意事项】:
            1. 仅提取具有长期记忆价值的内容，过滤掉临时、寒暄或单次任务的无效细节。
            2. 每一条提取信息必须独立完整。
            3. 分类 memoryType 仅限以下四种:
               - PREFERENCE: 用户偏好/习惯 (例如: "偏好使用 Spring Boot 4.0 和 JDK 21")
               - SEMANTIC: 核心事实/系统配置 (例如: "PostgreSQL 部署在 192.168.200.188，开启了 vector 扩展")
               - CORRECTION: 用户纠错或负反馈规约 (例如: "必须使用 CAST(? AS vector) 语法")
               - EPISODIC: 重要里程碑事件 (例如: "完成系统 Spring Boot 4.0 升级重构")
            
            请严格以 JSON 数组格式输出，不要包含 markdown 标记以外的任何多余解释:
            [
              {
                "memoryType": "PREFERENCE",
                "content": "用户偏好使用 Spring Boot 4.0 和 JDK 21",
                "importance": 8.0,
                "rationale": "用户在多轮会话中强调了该版本选型"
              }
            ]
            若无任何具有长期价值的记忆点，请输出空数组 []。
            """;

    private static final String DECIDE_PROMPT = """
            你是一个高级记忆状态机仲裁器（基于 Mem0 冲突消解协议）。
            现有用户历史相关的既有记忆，以及本次对话新提取的候选记忆。
            
            请对该候选事实做出决策裁决:
            - ADD: 全新事实，既有记忆中未包含且与既有记忆无冲突。
            - UPDATE: 该事实是既有某条记忆的更新、修正或演进（必须指定 targetMemoryId 和更新后的完整内容）。
            - DELETE: 用户明确指示遗忘、作废该既有记忆（必须指定 targetMemoryId）。
            - NOOP: 候选事实已被既有记忆完全涵盖，或者琐碎无价值，无需做任何变动。
            
            请严格以 JSON 对象输出:
            {
              "action": "ADD|UPDATE|DELETE|NOOP",
              "targetMemoryId": "既有记忆ID(UPDATE/DELETE时必填，ADD/NOOP留空)",
              "memoryType": "PREFERENCE|SEMANTIC|CORRECTION|EPISODIC",
              "content": "最终应保存的记忆内容",
              "importance": 1.0到10.0的浮点数,
              "rationale": "决策仲裁判定理由"
            }
            """;

    /**
     * 从当前对话中提炼记忆并进行冲突决策
     */
    public List<MemoryDecisionResult> analyzeAndDecide(Long userId, String userMsg, String assistantResp) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null || userId == null || !StringUtils.hasText(userMsg)) {
            return Collections.emptyList();
        }

        try {
            // 阶段 1: 提取候选事实
            String conversation = "用户: " + userMsg + "\n助手: " + (assistantResp != null ? assistantResp : "");
            String extractJson = ChatClient.create(chatModel)
                    .prompt()
                    .system(EXTRACT_PROMPT)
                    .user(conversation)
                    .call()
                    .content();

            List<MemoryCandidate> candidates = parseCandidates(extractJson);
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }

            // 阶段 2: 结合既有记忆做 Mem0 冲突裁决
            List<MemoryDecisionResult> decisions = new ArrayList<>();
            for (MemoryCandidate cand : candidates) {
                // 检索最相似的前 3 条长期记忆
                List<AiAgentMemory> existingMemories = longTermMemoryService.searchMemories(userId, cand.content, 3);

                StringBuilder decideInput = new StringBuilder();
                decideInput.append("【新提取的候选记忆】:\n")
                        .append("内容: ").append(cand.content).append("\n")
                        .append("类型: ").append(cand.memoryType).append("\n")
                        .append("提议重要度: ").append(cand.importance).append("\n\n");

                decideInput.append("【已存在的相似长期记忆库】:\n");
                if (existingMemories.isEmpty()) {
                    decideInput.append("(暂无相似记忆)\n");
                } else {
                    for (AiAgentMemory mem : existingMemories) {
                        decideInput.append("- ID: ").append(mem.getId())
                                .append(", 类型: ").append(mem.getMemoryType())
                                .append(", 内容: ").append(mem.getContent())
                                .append(", 重要度: ").append(mem.getImportance()).append("\n");
                    }
                }

                String decisionJson = ChatClient.create(chatModel)
                        .prompt()
                        .system(DECIDE_PROMPT)
                        .user(decideInput.toString())
                        .call()
                        .content();

                MemoryDecisionResult result = parseDecision(decisionJson);
                if (result != null && !"NOOP".equalsIgnoreCase(result.getAction())) {
                    decisions.add(result);
                }
            }

            return decisions;
        } catch (Exception e) {
            log.warn("Mem0 记忆提炼决策异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class MemoryCandidate {
        String memoryType;
        String content;
        Double importance;
        String rationale;
    }

    private List<MemoryCandidate> parseCandidates(String raw) {
        List<MemoryCandidate> list = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return list;
        }
        try {
            String clean = cleanJson(raw);
            JSONArray array = JSONUtil.parseArray(clean);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                MemoryCandidate c = new MemoryCandidate();
                c.memoryType = obj.getStr("memoryType", "SEMANTIC");
                c.content = obj.getStr("content");
                c.importance = obj.getDouble("importance", 5.0);
                c.rationale = obj.getStr("rationale");
                if (StringUtils.hasText(c.content)) {
                    list.add(c);
                }
            }
        } catch (Exception e) {
            log.debug("解析候选事实 JSON 失败: {}", e.getMessage());
        }
        return list;
    }

    private MemoryDecisionResult parseDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String clean = cleanJson(raw);
            JSONObject obj = JSONUtil.parseObj(clean);
            return MemoryDecisionResult.builder()
                    .action(obj.getStr("action", "NOOP").toUpperCase())
                    .targetMemoryId(obj.getStr("targetMemoryId"))
                    .memoryType(obj.getStr("memoryType", "SEMANTIC"))
                    .content(obj.getStr("content"))
                    .importance(obj.getDouble("importance", 5.0))
                    .rationale(obj.getStr("rationale"))
                    .build();
        } catch (Exception e) {
            log.debug("解析决策结果 JSON 失败: {}", e.getMessage());
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
