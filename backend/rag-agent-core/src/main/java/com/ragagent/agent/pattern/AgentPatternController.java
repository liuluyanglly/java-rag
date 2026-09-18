package com.ragagent.agent.pattern;

import com.ragagent.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent 5 种模式 API 控制器 (对齐语雀文档 15)
 */
@Tag(name = "Agent 5种经典模式 API", description = "Chain 链式、Parallelization 并行化、Routing 路由、Orchestrator-Workers 编排器、Evaluator-Optimizer 评估优化器")
@RestController
@RequestMapping("/api/agent/patterns")
@RequiredArgsConstructor
public class AgentPatternController {

    private final AgentPatternService patternService;

    @Operation(summary = "模式一：Chain 链式工作流")
    @PostMapping("/chain")
    public Mono<Result<AgentPatternService.ChainResult>> chain(@RequestBody SimpleTaskRequest request) {
        return Mono.fromCallable(() -> patternService.runChainPattern(request.getInput()))
                .map(Result::success);
    }

    @Operation(summary = "模式二：Parallelization 多专家并行化与聚合")
    @PostMapping("/parallel")
    public Mono<Result<AgentPatternService.ParallelResult>> parallel(@RequestBody ParallelRequest request) {
        List<String> perspectives = (request.getPerspectives() == null || request.getPerspectives().isEmpty())
                ? List.of("技术架构", "安全合规", "成本预算")
                : request.getPerspectives();
        return Mono.fromCallable(() -> patternService.runParallelPattern(request.getTopic(), perspectives))
                .map(Result::success);
    }

    @Operation(summary = "模式三：Routing 意图智能路由分流")
    @PostMapping("/routing")
    public Mono<Result<AgentPatternService.RoutingResult>> routing(@RequestBody SimpleTaskRequest request) {
        return Mono.fromCallable(() -> patternService.runRoutingPattern(request.getInput()))
                .map(Result::success);
    }

    @Operation(summary = "模式四：Orchestrator-Workers 编排器动态拆分与合成")
    @PostMapping("/orchestrator")
    public Mono<Result<AgentPatternService.OrchestrationResult>> orchestrator(@RequestBody SimpleTaskRequest request) {
        return Mono.fromCallable(() -> patternService.runOrchestratorPattern(request.getInput()))
                .map(Result::success);
    }

    @Operation(summary = "模式五：Evaluator-Optimizer 评估器-优化器闭环迭代")
    @PostMapping("/eval-optimizer")
    public Mono<Result<AgentPatternService.EvaluatorOptimizerResult>> evalOptimizer(
            @RequestBody SimpleTaskRequest request,
            @RequestParam(defaultValue = "3") int maxIterations) {
        return Mono.fromCallable(() -> patternService.runEvaluatorOptimizerPattern(request.getInput(), maxIterations))
                .map(Result::success);
    }

    @Data
    public static class SimpleTaskRequest {
        private String input;
    }

    @Data
    public static class ParallelRequest {
        private String topic;
        private List<String> perspectives;
    }
}
