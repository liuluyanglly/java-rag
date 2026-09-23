package com.ragagent.graph.controller;

import com.ragagent.common.result.Result;
import com.ragagent.graph.GraphState;
import com.ragagent.graph.service.MultiAgentGraphOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h3>Spring AI Alibaba Graph 多智能体协同工作流控制器</h3>
 *
 * @author Java-RAG Team
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/multi-agent")
@RequiredArgsConstructor
@Tag(name = "Multi-Agent Graph 工作流", description = "基于 Spring AI Alibaba Graph 的多智能体协同与 Loop 循环自愈编排 API")
public class MultiAgentWorkflowController {

    private final MultiAgentGraphOrchestrator orchestrator;

    @Data
    public static class ExecuteRequest {
        private String topic;
    }

    @PostMapping("/execute")
    @Operation(summary = "触发多智能体协同工作流 (支持 Loop 循环反思自愈)", description = "由 StateGraph 统一调度 PlannerAgent -> CoderExecutorAgent -> ReviewerCriticAgent -> SynthesizerAgent")
    public Result<Map<String, Object>> execute(@RequestBody ExecuteRequest request) {
        String topic = request != null && request.getTopic() != null ? request.getTopic().trim() : "";
        if (topic.isEmpty()) {
            return Result.error("任务目标 (topic) 不能为空");
        }

        List<String> executionLogs = new ArrayList<>();
        GraphState finalState = orchestrator.executeWorkflow(topic, executionLogs::add);

        Map<String, Object> result = new HashMap<>();
        result.put("threadId", finalState.getThreadId());
        result.put("topic", finalState.getTopic());
        result.put("planSteps", finalState.getPlanSteps());
        result.put("loopCount", finalState.get("loopCount"));
        result.put("reviewScore", finalState.get("reviewScore"));
        result.put("reviewDecision", finalState.get("reviewDecision"));
        result.put("reviewFeedback", finalState.get("reviewFeedback"));
        result.put("finalReport", finalState.getFinalReport());
        result.put("thoughts", finalState.getThoughts());
        result.put("executionLogs", executionLogs);

        return Result.success(result);
    }
}
