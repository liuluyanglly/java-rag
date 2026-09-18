package com.ragagent.rag.controller;

import com.ragagent.common.result.Result;
import com.ragagent.rag.service.RagEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型 RAG 评测与幻觉质检中心 (Spring AI 2.0 规范)
 * 提供问答事实性核验 (Fact-Checking)、相关性评估 (Relevancy) 与批量评测
 */
@Slf4j
@Tag(name = "RAG 质量与幻觉评测中心")
@RestController
@RequestMapping("/api/rag/eval")
@RequiredArgsConstructor
public class RagEvaluationController {

    private final RagEvaluationService evaluationService;

    @Data
    @Schema(name = "SingleEvalRequest", description = "单轮问答评测入参")
    public static class SingleEvalRequest {
        @Schema(description = "用户原始提问", example = "退订需要多少费用？", requiredMode = Schema.RequiredMode.REQUIRED)
        private String query;

        @Schema(description = "检索到的切片上下文", example = "取消预订：最晚在起飞前48小时取消，取消费用：经济舱75美元，豪华经济舱50美元。")
        private String context;

        @Schema(description = "大模型输出的回答", example = "根据规定，退订经济舱需要支付75美元的手续费。", requiredMode = Schema.RequiredMode.REQUIRED)
        private String response;
    }

    @Operation(summary = "单条 RAG 问答事实性与相关度评测", description = "基于 FactCheckingEvaluator 和 RelevancyEvaluator 综合检测事实性幻觉与偏题风险")
    @PostMapping("/check")
    public Result<RagEvaluationService.RagEvalResult> checkSingle(@RequestBody SingleEvalRequest req) {
        RagEvaluationService.RagEvalResult result = evaluationService.evaluate(req.getQuery(), req.getContext(), req.getResponse());
        return Result.success("评测完成", result);
    }

    @Data
    @Schema(name = "BatchEvalRequest", description = "批量评测入参")
    public static class BatchEvalRequest {
        @Schema(description = "待测试用例集合")
        private List<SingleEvalRequest> items;
    }

    @Operation(summary = "批量 RAG 质量巡检", description = "对一批历史会话或测试用例进行自动化质量与幻觉通过率评估")
    @PostMapping("/batch")
    public Result<RagEvaluationService.BatchEvalSummary> batchCheck(@RequestBody BatchEvalRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return Result.error("评测用例列表不能为空");
        }

        int total = req.getItems().size();
        int passed = 0;
        double sumScore = 0.0;
        List<RagEvaluationService.BatchItemResult> itemResults = new ArrayList<>();

        for (SingleEvalRequest item : req.getItems()) {
            RagEvaluationService.RagEvalResult res = evaluationService.evaluate(item.getQuery(), item.getContext(), item.getResponse());
            if (res.isPass()) {
                passed++;
            }
            sumScore += res.getTotalScore();
            itemResults.add(RagEvaluationService.BatchItemResult.builder()
                    .query(item.getQuery())
                    .pass(res.isPass())
                    .score(res.getTotalScore())
                    .feedback(res.getFactFeedback() + " | " + res.getRelevancyFeedback())
                    .build());
        }

        double passRate = Math.round((double) passed / total * 1000.0) / 10.0;
        double avgScore = Math.round(sumScore / total * 100.0) / 100.0;

        RagEvaluationService.BatchEvalSummary summary = RagEvaluationService.BatchEvalSummary.builder()
                .totalCount(total)
                .passedCount(passed)
                .passRate(passRate)
                .averageScore(avgScore)
                .itemResults(itemResults)
                .build();

        return Result.success("批量评测完成", summary);
    }
}
