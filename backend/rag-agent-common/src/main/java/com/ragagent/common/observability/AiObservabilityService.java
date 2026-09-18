package com.ragagent.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI 2.0 实时监控与观测度量服务 (对齐语雀文档 14)
 * 基于 Micrometer + Prometheus 语义约定统一采集：
 * 1. ChatClient 操作性能 (spring.ai.chat.client.operation)
 * 2. Token 消耗统计与成本预估 (gen_ai.client.token.usage)
 * 3. 向量存储操作性能 (spring.ai.vector.store)
 * 4. Advisor 执行链路性能 (spring.ai.advisor)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiObservabilityService {

    private final MeterRegistry meterRegistry;

    /**
     * 获取系统当前 Spring AI 全链路可观测性度量总览
     */
    public AiMetricsSummary getMetricsSummary() {
        // 1. ChatClient 调用统计
        long chatCount = 0;
        double chatTotalSeconds = 0.0;
        double chatMaxSeconds = 0.0;

        var chatTimer = meterRegistry.find("spring.ai.chat.client.operation").timer();
        if (chatTimer != null) {
            chatCount = chatTimer.count();
            chatTotalSeconds = chatTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS);
            chatMaxSeconds = chatTimer.max(java.util.concurrent.TimeUnit.SECONDS);
        }

        // 2. Token 消耗统计 (区分 input / output / total)
        long inputTokens = getTokenCount("input");
        long outputTokens = getTokenCount("output");
        long totalTokens = getTokenCount("total");
        if (totalTokens == 0 && (inputTokens > 0 || outputTokens > 0)) {
            totalTokens = inputTokens + outputTokens;
        }

        // 3. 向量数据库指标
        long vectorOpsCount = 0;
        var vectorTimer = meterRegistry.find("spring.ai.vector.store").timer();
        if (vectorTimer != null) {
            vectorOpsCount = vectorTimer.count();
        }

        // 4. 预估成本 (基于通用模型平均费率：输入 0.008 元/千 token，输出 0.024 元/千 token)
        double estimatedCostRmb = (inputTokens * 0.000008) + (outputTokens * 0.000024);

        Map<String, Object> details = new HashMap<>();
        details.put("advisorMetricsCount", countMeters("spring.ai.advisor"));
        details.put("modelOperationCount", countMeters("gen_ai.client.operation"));

        return AiMetricsSummary.builder()
                .chatClientCalls(chatCount)
                .chatTotalTimeSeconds(Math.round(chatTotalSeconds * 100.0) / 100.0)
                .chatAvgLatencyMs(chatCount > 0 ? Math.round((chatTotalSeconds / chatCount) * 1000.0) : 0)
                .chatMaxLatencyMs(Math.round(chatMaxSeconds * 1000.0))
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .vectorStoreOps(vectorOpsCount)
                .estimatedCostRmb(Math.round(estimatedCostRmb * 10000.0) / 10000.0)
                .additionalDetails(details)
                .build();
    }

    private long getTokenCount(String tokenType) {
        var counter = Search.in(meterRegistry)
                .name("gen_ai.client.token.usage")
                .tag("gen_ai.token.type", tokenType)
                .counter();
        return counter != null ? (long) counter.count() : 0L;
    }

    private int countMeters(String meterPrefix) {
        return meterRegistry.find(meterPrefix).meters().size();
    }

    @Data
    @Builder
    public static class AiMetricsSummary {
        private long chatClientCalls;
        private double chatTotalTimeSeconds;
        private double chatAvgLatencyMs;
        private double chatMaxLatencyMs;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private long vectorStoreOps;
        private double estimatedCostRmb;
        private Map<String, Object> additionalDetails;
    }
}
