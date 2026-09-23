package com.ragagent.graph.agent;

import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <h3>2. 研发执行智能体 (Coder / Executor Agent)</h3>
 * <p>
 * <b>角色职责：</b>
 * 资深全栈研发工程师，负责具体代码实现与工程落地。
 * 具备自我修复（Self-Correction）意识：在初始阶段按规划生成完整实现；
 * 若被审查智能体打回，则结合审查反馈（Review Feedback）针对性重构缺陷代码。
 *
 * @author Java-RAG Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoderExecutorAgent implements SubAgent {

    private final ChatClient.Builder chatClientBuilder;

    @Override
    public String getRoleName() {
        return "CoderExecutorAgent";
    }

    @Override
    public String getDescription() {
        return "资深研发工程师，负责根据大纲编写高质量代码，并在 Loop 回路中针对审查意见精准修复缺陷";
    }

    @Override
    public GraphState execute(GraphState state) {
        String topic = state.getTopic();
        String feedback = (String) state.get("reviewFeedback");
        Integer loopCount = (Integer) state.getOrDefault("loopCount", 0);

        boolean isLoopRerun = StringUtils.hasText(feedback) && loopCount > 0;
        log.info("【CoderExecutorAgent】开始执行实现任务: loopCount={}, 是否属于反思回路={}", loopCount, isLoopRerun);

        String systemPrompt;
        String userPrompt;

        if (isLoopRerun) {
            state.addThought("【CoderExecutorAgent】收到 ReviewerCritic 的缺陷审查意见 (第 " + loopCount + " 轮 Loop 迭代)，正在定向修复与重构代码...");
            systemPrompt = "你是一名追求极致代码质量的高级工程师。上一轮代码审查未能通过，请针对审查员指出的具体缺陷、漏洞或缺失逻辑，进行针对性的深度修复和重构，确保代码兼顾健壮性与可读性。";
            userPrompt = """
                    【原始任务需求】：
                    %s
                    
                    【规划执行步骤】：
                    %s
                    
                    【审查员打回反馈与缺陷清单】：
                    %s
                    
                    请输出修复并完善后的全量高质量代码实现与设计说明。
                    """.formatted(topic, String.join("\n", state.getPlanSteps()), feedback);
        } else {
            state.addThought("【CoderExecutorAgent】正在根据规划步骤编写核心代码实现...");
            systemPrompt = "你是一名资深全栈工程师，擅长编写优雅、高内聚低耦合、附带完备注释与类型安全的生产级代码。";
            userPrompt = """
                    【任务需求】：
                    %s
                    
                    【规划步骤】：
                    %s
                    
                    请严格按照上述步骤编写完整的生产级代码实现，包含核心类定义、关键逻辑与异常处理。
                    """.formatted(topic, String.join("\n", state.getPlanSteps()));
        }

        String executionResult = "";
        try {
            ChatClient chatClient = chatClientBuilder.build();
            executionResult = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("【CoderExecutorAgent】代码生成异常，采用兜底实现: {}", e.getMessage());
            executionResult = "// 自动生成核心业务框架代码\npublic class Solution {\n    // TODO: 实现核心逻辑\n}";
        }

        state.put("executionResult", executionResult);
        state.addThought("【CoderExecutorAgent】已完成代码产出 (字符数: " + (executionResult != null ? executionResult.length() : 0) + ")，提交 ReviewerCriticAgent 进行严苛质检。");

        return state;
    }
}
