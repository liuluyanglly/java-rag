package com.ragagent.graph.service;

import com.ragagent.graph.ConditionalEdge;
import com.ragagent.graph.GraphState;
import com.ragagent.graph.PostgresCheckpointer;
import com.ragagent.graph.StateGraph;
import com.ragagent.graph.agent.CoderExecutorAgent;
import com.ragagent.graph.agent.PlannerAgent;
import com.ragagent.graph.agent.ReviewerCriticAgent;
import com.ragagent.graph.agent.SynthesizerAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * <h3>Spring AI Alibaba Graph 多智能体协同编排器 (Multi-Agent Graph Orchestrator)</h3>
 * <p>
 * <b>学习核心知识点：</b>
 * <ol>
 *   <li><b>有状态图拓扑 (StateGraph Topology)</b>：
 *       <br>采用 Spring AI Alibaba Graph 规范，将 PlannerAgent、CoderExecutorAgent、ReviewerCriticAgent、SynthesizerAgent 组装为有向有环状态图。</li>
 *   <li><b>动态条件边与 Loop 自愈回路 (Conditional Edge & Loop)</b>：
 *       <br>通过 {@code addConditionalEdge("ReviewerCriticAgent", ...)} 动态裁决下一跳。若质检不通过，携带反馈指令循环回退至 CoderExecutorAgent，形成高质量自适应修正闭环。</li>
 *   <li><b>熔断机制 (Circuit Breaker)</b>：
 *       <br>当达到 {@code maxLoops} 最大循环阈值时，自动安全熔断并进入交付节点，杜绝 Token 浪费与死循环。</li>
 * </ol>
 *
 * @author Java-RAG Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentGraphOrchestrator {

    private final PlannerAgent plannerAgent;
    private final CoderExecutorAgent coderExecutorAgent;
    private final ReviewerCriticAgent reviewerCriticAgent;
    private final SynthesizerAgent synthesizerAgent;

    @Autowired(required = false)
    private PostgresCheckpointer checkpointer;

    /**
     * 构建由 Spring AI Alibaba Graph 管理的多智能体状态图
     */
    public StateGraph buildStateGraph() {
        StateGraph graph = new StateGraph();
        if (checkpointer != null) {
            graph.withCheckpointer(checkpointer);
        }

        // 1. 注册 4 个专职子智能体节点
        graph.addNode("PlannerAgent", plannerAgent)
             .addNode("CoderExecutorAgent", coderExecutorAgent)
             .addNode("ReviewerCriticAgent", reviewerCriticAgent)
             .addNode("SynthesizerAgent", synthesizerAgent);

        // 2. 配置工作流起点与固定流转
        graph.setEntryPoint("PlannerAgent");
        graph.addEdge("PlannerAgent", "CoderExecutorAgent");
        graph.addEdge("CoderExecutorAgent", "ReviewerCriticAgent");

        // 3. 关键配置：基于条件边 (ConditionalEdge) 驱动 Loop 循环回路
        graph.addConditionalEdge("ReviewerCriticAgent", state -> {
            String decision = (String) state.get("reviewDecision");
            Integer loopCount = (Integer) state.getOrDefault("loopCount", 0);
            Integer maxLoops = (Integer) state.getOrDefault("maxLoops", 3);

            // 质检不通过且未超过最大重试轮次：触发 Loop 回路，跳回 CoderExecutorAgent 重构
            if (!"PASS".equalsIgnoreCase(decision) && loopCount < maxLoops) {
                log.info("【StateGraph】ReviewerCriticAgent 判定不合格 (第 {} 轮 Loop)，触发反思回路 -> CoderExecutorAgent", loopCount);
                return "CoderExecutorAgent";
            }

            // 质检通过或触发最大循环熔断：流转至 SynthesizerAgent 生成最终交付报告
            log.info("【StateGraph】质检通过或达到熔断轮次 ({} 次)，流转至 -> SynthesizerAgent", loopCount);
            return "SynthesizerAgent";
        });

        // 4. 交付智能体流转至 END
        graph.addEdge("SynthesizerAgent", ConditionalEdge.END);

        return graph;
    }

    /**
     * 驱动多智能体协同流水线执行
     *
     * @param topic        用户任务目标
     * @param stepNotifier 步骤实时通知监听器
     * @return 最终全局状态
     */
    public GraphState executeWorkflow(String topic, Consumer<String> stepNotifier) {
        String threadId = UUID.randomUUID().toString().replace("-", "");
        GraphState initialState = GraphState.builder()
                .threadId(threadId)
                .topic(topic)
                .build();

        StateGraph graph = buildStateGraph();
        log.info("【MultiAgentOrchestrator】启动多智能体协同流水线: threadId={}, topic='{}'", threadId, topic);

        return graph.invoke(initialState, stepNotifier);
    }
}
