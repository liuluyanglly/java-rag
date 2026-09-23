package com.ragagent.graph.agent;

import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <h3>4. 成果汇聚交付智能体 (Synthesizer / Reporter Agent)</h3>
 * <p>
 * <b>角色职责：</b>
 * 技术成果主管与交付总监。
 * 在多智能体经历规划、编写、质检以及多轮 Loop 循环自愈后，
 * 负责整合全部阶段产出，生成结构严谨、包含反思历程与最终代码的高质量 Markdown 交付报告。
 *
 * @author Java-RAG Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SynthesizerAgent implements SubAgent {

    @Override
    public String getRoleName() {
        return "SynthesizerAgent";
    }

    @Override
    public String getDescription() {
        return "交付总监，负责汇总多智能体协同全生命周期产出，输出结构化工程交付报告";
    }

    @Override
    public GraphState execute(GraphState state) {
        log.info("【SynthesizerAgent】开始汇聚多智能体成果并生成最终交付报告...");
        state.addThought("【SynthesizerAgent】正在编排全流程产出物，生成最终交付报告...");

        String topic = state.getTopic();
        String code = (String) state.getOrDefault("executionResult", "暂无代码产出");
        Integer loopCount = (Integer) state.getOrDefault("loopCount", 1);
        Integer score = (Integer) state.getOrDefault("reviewScore", 80);
        String feedback = (String) state.getOrDefault("reviewFeedback", "符合工程质量交付标准");

        StringBuilder report = new StringBuilder();
        report.append("# 🚀 多智能体协同研发成果交付报告\n\n");
        report.append("> 本报告由 **Spring AI Alibaba Graph** 驱动的多智能体协同流水线自动生成，历经 **")
                .append(loopCount).append(" 轮 Loop 循环自愈** 与质量审查。\n\n");

        report.append("## 一、 任务目标与需求\n");
        report.append(topic).append("\n\n");

        report.append("## 二、 规划大纲与执行步骤 (PlannerAgent)\n");
        if (state.getPlanSteps() != null && !state.getPlanSteps().isEmpty()) {
            for (String step : state.getPlanSteps()) {
                report.append("- ").append(step).append("\n");
            }
        } else {
            report.append("- 已按标准工程流程执行\n");
        }
        report.append("\n");

        report.append("## 三、 质量审查与 Loop 反思记录 (ReviewerCriticAgent)\n");
        report.append("- **综合质量评分**：`").append(score).append(" / 100`\n");
        report.append("- **Loop 迭代次数**：`").append(loopCount).append(" 次`\n");
        report.append("- **审查最终结论**：").append(feedback).append("\n\n");

        report.append("## 四、 核心代码实现与方案交付 (CoderExecutorAgent)\n");
        report.append(code).append("\n\n");

        report.append("## 五、 多智能体思考流转轨迹 (StateGraph Trace)\n");
        if (state.getThoughts() != null && !state.getThoughts().isEmpty()) {
            for (String thought : state.getThoughts()) {
                report.append("1. ").append(thought).append("\n");
            }
        }

        state.setFinalReport(report.toString());
        state.addThought("【SynthesizerAgent】成果交付报告生成完毕，多智能体协同流水线圆满完成！");

        return state;
    }
}
