package com.ragagent.agent.tools;

import com.ragagent.memory.entity.AiAgentMemory;
import com.ragagent.memory.service.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 长期记忆智能体自主管理工具 (借鉴 Spring-AI-Agent-Utils 架构规范)
 * <p>
 * 赋能智能体在执行复杂推理、多步规划或长效交互时：
 * 1. 主动唤醒长期记忆 (Semantic & Preference Memory Retrieval)
 * 2. 主动沉淀用户画像与操作偏好 (Agent Autonomous Remembering)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongTermMemoryTool {

    // 注入长期记忆底层服务 (基于 pgvector 混合检索与艾宾浩斯时间衰减)
    private final LongTermMemoryService longTermMemoryService; // 长期记忆服务中枢

    /**
     * 智能体自主检索长期记忆工具
     *
     * @param userId 用户唯一标识
     * @param query  需要检索的事实、偏好或纠错准则关键词
     * @return 检索命中的长期记忆摘要列表
     */
    @Tool(description = "检索用户的长期记忆库（包含用户的长期偏好、系统技术栈事实、自省纠错准则等），帮助智能体提供个性化与精准决策")
    public List<String> recallMemories(Long userId, String query) {
        if (userId == null || !StringUtils.hasText(query)) { // 校验入参有效性
            return Collections.emptyList();                  // 参数缺失时安全返回空列表
        }

        log.info("[LongTermMemoryTool] Agent 主动唤醒长期记忆: userId={}, query={}", userId, query); // 记录工具执行轨迹

        List<AiAgentMemory> memories = longTermMemoryService // 调用长期记忆底层服务
                .searchMemories(userId, query, 5);           // 执行基于斯坦福小镇算法的混合向量加权检索，召回 Top5

        if (memories == null || memories.isEmpty()) {        // 检查召回结果是否为空
            return List.of("未检索到与该主题相关的用户长期偏好或历史事实。"); // 友好返回提示
        }

        return memories.stream()                             // 开启流式转换
                .map(m -> String.format("[%s] (重要度: %.1f) %s", 
                        m.getMemoryType(),                   // 记忆类别标签
                        m.getImportance() != null ? m.getImportance() : 5.0, // 重要度评分
                        m.getContent()))                     // 核心记忆正文
                .toList();                                   // 收集转换为字符串列表
    }

    /**
     * 智能体自主记录/沉淀长期偏好与规约工具
     *
     * @param userId     用户唯一标识
     * @param memoryType 记忆类型：PREFERENCE(偏好), SEMANTIC(事实), CORRECTION(纠错准则)
     * @param content    需要永久记忆的具体内容
     * @param importance 记忆重要度 (1.0 ~ 10.0，默认 7.0)
     * @return 记忆保存结果提示
     */
    @Tool(description = "当用户表达了明确的使用偏好、技术约束、业务规则或纠错意见时，智能体主动将其永久保存到长期记忆库")
    public String rememberUserInfo(Long userId, String memoryType, String content, Double importance) {
        if (userId == null || !StringUtils.hasText(content)) { // 校验关键参数
            return "保存失败：用户ID或记忆内容不能为空";         // 拦截非法入参
        }

        String type = StringUtils.hasText(memoryType) ? memoryType.toUpperCase().trim() : "PREFERENCE"; // 规范化类型
        double score = (importance != null && importance >= 1.0 && importance <= 10.0) ? importance : 7.0; // 限制权重范围

        log.info("[LongTermMemoryTool] Agent 主动沉淀长期记忆: userId={}, type={}, content={}", userId, type, content); // 打印日志

        AiAgentMemory memory = longTermMemoryService.addMemory( // 调用长期记忆底层持久化
                userId,                                         // 用户 ID
                null,                                           // agentId 可选
                null,                                           // 会话 ID
                type,                                           // 记忆类型标签
                content.trim(),                                 // 记忆内容纯文本
                score,                                          // 重要度权重
                "{\"source\":\"AGENT_UTILS_TOOL\"}"             // 来源元数据标记
        );

        if (memory != null) { // 检查持久化是否成功
            return String.format("已成功永久记录到长期记忆库！[ID: %s, 类型: %s, 重要度: %.1f]", 
                    memory.getId(), memory.getMemoryType(), memory.getImportance()); // 返回成功响应
        } else {
            return "长期记忆保存失败，请稍后重试。";            // 返回失败响应
        }
    }
}
