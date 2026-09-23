package com.ragagent.graph.agent;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <h3>1. 规划智能体 (Planner Agent)</h3>
 * <p>
 * <b>角色职责：</b>
 * 作为多智能体协同流水线的总指挥官，负责将用户粗粒度的目标拆解为结构化、分阶段的执行步骤。
 * 初始化多轮 Loop 迭代上下文与任务树。
 *
 * @author Java-RAG Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent implements SubAgent {

    private final ChatClient.Builder chatClientBuilder;

    @Override
    public String getRoleName() {
        return "PlannerAgent";
    }

    @Override
    public String getDescription() {
        return "需求拆解与架构规划专家，负责将宏观任务分解为明确可执行的阶段大纲";
    }

    @Override
    public GraphState execute(GraphState state) {
        String topic = state.getTopic();
        log.info("【PlannerAgent】启动任务拆解与规划: topic='{}'", topic);

        state.addThought("【PlannerAgent】正在分析任务目标，构建分步执行计划...");

        String prompt = """
                你是一名资深的技术架构师与任务规划专家。
                请针对用户的任务需求，将其拆解为 3~5 个清晰、递进、可验证的执行步骤。
                必须严格以 JSON 字符串数组格式返回，例如：
                ["1. 模块架构与数据模型定义", "2. 核心服务接口与业务逻辑实现", "3. 异常边界防御与单元测试验证"]
                
                用户任务需求：
                %s
                """.formatted(topic);

        List<String> steps = new ArrayList<>();
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(prompt).call().content();
            if (StringUtils.hasText(response)) {
                String cleanJson = response.replaceAll("```json|```", "").trim();
                if (JSONUtil.isTypeJSONArray(cleanJson)) {
                    JSONArray arr = JSONUtil.parseArray(cleanJson);
                    for (Object obj : arr) {
                        steps.add(String.valueOf(obj));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【PlannerAgent】大模型规划输出异常，启用兜底规划模板: {}", e.getMessage());
        }

        if (steps.isEmpty()) {
            steps = List.of(
                    "1. 需求分析与输入输出边界定义",
                    "2. 核心架构设计与详细代码实现",
                    "3. 边界异常防御与自愈闭环测试"
            );
        }

        state.setPlanSteps(steps);
        // 初始化多智能体 Loop 循环计数器
        state.put("loopCount", 0);
        state.put("maxLoops", 3); // 最大允许循环自愈重试 3 次，防止死循环
        state.addThought("【PlannerAgent】成功生成 " + steps.size() + " 个执行阶段，已转交 CoderExecutorAgent 执行。");

        return state;
    }
}
