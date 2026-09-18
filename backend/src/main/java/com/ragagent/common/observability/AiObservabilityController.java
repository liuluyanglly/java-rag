package com.ragagent.common.observability;

import com.ragagent.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Spring AI 2.0 实时监控与观测 API 控制器
 */
@Tag(name = "AI 可观测性监控", description = "提供基于 Micrometer 的 Token 消耗统计、调用频次、延迟分布与成本预估")
@RestController
@RequestMapping("/api/ai/metrics")
@RequiredArgsConstructor
public class AiObservabilityController {

    private final AiObservabilityService observabilityService;

    @Operation(summary = "获取当前系统 Spring AI 监控统计与成本看板")
    @GetMapping("/summary")
    public Mono<Result<AiObservabilityService.AiMetricsSummary>> getSummary() {
        return Mono.fromCallable(observabilityService::getMetricsSummary)
                .map(Result::success);
    }
}

