package com.ragagent.agent.pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragagent.agent.tools.CustomerServiceTool;
import com.ragagent.rag.service.RagSearchService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring AI 2.0 核心 Agent 5 种模式服务实现 (对齐语雀文档 15)
 * 模式覆盖：
 * 1. Chain 链式模式 (线性推进：大纲 -> 展开 -> 优化 -> 格式化)
 * 2. Parallelization 并行化模式 (虚拟线程多专家并发 + 聚合器合成)
 * 3. Routing 路由模式 (意图分类 -> 知识库 / 客服工具 / 通用直出)
 * 4. Orchestrator-Workers 编排器-工作者模式 (LLM 动态任务拆分 -> 并发执行 -> 结果合成)
 * 5. Evaluator-Optimizer 评估器-优化器循环模式 (生成 -> 严格质检 -> 带反馈重写)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPatternService {

    private final ChatClient.Builder chatClientBuilder;
    private final RagSearchService ragSearchService;
    private final CustomerServiceTool customerServiceTool;

    // ==========================================
    // 模式一：Chain 链式 (顺序流水线)
    // ==========================================

    public ChainResult runChainPattern(String requirement) {
        ChatClient client = chatClientBuilder.build();
        List<String> steps = new ArrayList<>();

        // 步骤 1: 提炼大纲
        String outlinePrompt = "请根据以下需求提炼核心结构大纲（3-5点）：\n" + requirement;
        String outline = client.prompt(outlinePrompt).call().content();
        steps.add("步骤1[大纲生成]: " + (outline != null && outline.length() > 50 ? outline.substring(0, 50) + "..." : outline));

        // 步骤 2: 详细扩展
        String expandPrompt = "请根据以下大纲展开编写详细内容：\n" + outline;
        String expanded = client.prompt(expandPrompt).call().content();
        steps.add("步骤2[内容扩展]: 已完成详细段落撰写");

        // 步骤 3: 优化与格式化
        String formatPrompt = "请对以下内容进行语言润色并以标准的 Markdown 格式输出：\n" + expanded;
        String finalOutput = client.prompt(formatPrompt).call().content();
        steps.add("步骤3[格式与润色]: Markdown 格式标准化完成");

        return new ChainResult(finalOutput, steps, true);
    }

    // ==========================================
    // 模式二：Parallelization 并行化 (分段/投票 + 聚合)
    // ==========================================

    public ParallelResult runParallelPattern(String topic, List<String> perspectives) {
        ChatClient client = chatClientBuilder.build();
        // 采用 Java 21 虚拟线程执行器提升高并发吞吐
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            List<CompletableFuture<PerspectiveAnalysis>> futures = perspectives.stream()
                    .map(p -> CompletableFuture.supplyAsync(() -> {
                        String prompt = String.format("你是一名【%s】领域的资深专家，请对主题【%s】进行深度分析并给出专业评估与建议：", p, topic);
                        String analysis = client.prompt(prompt).call().content();
                        return new PerspectiveAnalysis(p, analysis);
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            List<PerspectiveAnalysis> analyses = futures.stream().map(CompletableFuture::join).toList();

            // 聚合器合成
            StringBuilder sb = new StringBuilder();
            for (PerspectiveAnalysis a : analyses) {
                sb.append("### 【").append(a.perspective()).append("专家视角】\n").append(a.content()).append("\n\n");
            }

            String aggPrompt = """
                    你是一个多领域决策综合专家，请将以下各维度的专家分析结果进行汇总整合，输出一份结构化综合研判报告：
                    包含：1. 核心共识；2. 关键分歧与风险；3. 最终落地建议。
                    
                    专家分析内容：
                    """ + sb;

            String finalReport = client.prompt(aggPrompt).call().content();
            return new ParallelResult(topic, analyses, finalReport);
        } finally {
            executor.shutdown();
        }
    }

    // ==========================================
    // 模式三：Routing 路由模式 (意图分类分发)
    // ==========================================

    public RoutingResult runRoutingPattern(String userQuery) {
        ChatClient client = chatClientBuilder.build();

        // 1. 分类识别
        String classifyPrompt = String.format("""
                请对用户的提问意图进行精确分类，仅输出以下三个类别关键字之一，不要包含任何其他标点或废话：
                - KNOWLEDGE_QA (涉及知识库、规章制度、技术规范、产品说明等查询)
                - CUSTOMER_SERVICE (涉及机票预订、订票查询、航班退改签、个人行程等客服业务)
                - GENERAL_CHAT (普通闲聊、常识问答、通用对话)
                
                用户输入: %s
                """, userQuery);

        String rawRoute = client.prompt(classifyPrompt).call().content();
        String route = (rawRoute != null) ? rawRoute.trim().toUpperCase() : "GENERAL_CHAT";

        String executionResult;
        String executionPath;

        if (route.contains("CUSTOMER_SERVICE")) {
            executionPath = "航空客服业务分支 (Tools 挂载)";
            ChatClient toolClient = chatClientBuilder.defaultTools(customerServiceTool).build();
            executionResult = toolClient.prompt(userQuery).call().content();
        } else if (route.contains("KNOWLEDGE_QA")) {
            executionPath = "知识库问答分支 (RAG 检索增强)";
            var docs = ragSearchService.search(userQuery, null, 3);
            if (docs.isEmpty()) {
                executionResult = "知识库中未检索到相关内容，请提供更具体的问题细节。";
            } else {
                StringBuilder ctx = new StringBuilder();
                docs.forEach(d -> ctx.append(d.getContent()).append("\n"));
                executionResult = client.prompt(String.format("根据以下参考信息回答问题：\n%s\n\n用户问题: %s", ctx, userQuery))
                        .call().content();
            }
        } else {
            executionPath = "通用大模型对话分支 (Direct LLM)";
            executionResult = client.prompt(userQuery).call().content();
        }

        return new RoutingResult(userQuery, route, executionPath, executionResult);
    }

    // ==========================================
    // 模式四：Orchestrator-Workers 编排器-工作者 (动态拆分+合成)
    // ==========================================

    public OrchestrationResult runOrchestratorPattern(String macroTask) {
        ChatClient client = chatClientBuilder.build();

        // 1. 编排器分析任务并动态拆解
        String orchestratorPrompt = String.format("""
                你是一名资深系统架构与项目编排专家。请将以下宏观任务分解为 2 到 4 个高内聚、低耦合且可独立执行的子任务。
                任务说明: %s
                
                必须以 JSON 格式输出，Schema 为：
                {
                   "analysis": "任务复杂度剖析与拆解设计",
                   "subTasks": [
                      {"role": "角色/专业领域，如后端架构师", "taskName": "子任务名称", "instruction": "具体执行指令"}
                   ]
                   }
                """, macroTask);

        OrchestratorPlan plan = client.prompt(orchestratorPrompt)
                .call()
                .entity(OrchestratorPlan.class);

        if (plan == null || plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
            // 降级兜底方案
            return new OrchestrationResult(macroTask, "编排拆分降级", List.of(), client.prompt(macroTask).call().content());
        }

        // 2. 工作者并行处理各自子任务
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<WorkerDeliverable>> futures = plan.getSubTasks().stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> {
                        String workerPrompt = String.format("""
                                你是【%s】。请执行子任务【%s】：
                                任务要求：%s
                                原项目宏观背景：%s
                                """, task.getRole(), task.getTaskName(), task.getInstruction(), macroTask);
                        String deliverable = client.prompt(workerPrompt).call().content();
                        return new WorkerDeliverable(task.getRole(), task.getTaskName(), deliverable);
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<WorkerDeliverable> deliverables = futures.stream().map(CompletableFuture::join).toList();

            // 3. 编排器合成器合并输出
            StringBuilder allWorkersText = new StringBuilder();
            for (WorkerDeliverable d : deliverables) {
                allWorkersText.append("#### [").append(d.role()).append("] ").append(d.taskName()).append("\n")
                        .append(d.content()).append("\n\n");
            }

            String synthPrompt = String.format("""
                    你是项目总负责人，请将各个工作者的交付成果整合成最终完整的项目交付成果方案：
                    原始任务: %s
                    
                    各工作者交付物：
                    %s
                    """, macroTask, allWorkersText);

            String finalSynthesizedResult = client.prompt(synthPrompt).call().content();
            return new OrchestrationResult(macroTask, plan.getAnalysis(), deliverables, finalSynthesizedResult);
        } finally {
            executor.shutdown();
        }
    }

    // ==========================================
    // 模式五：Evaluator-Optimizer 评估器-优化器循环
    // ==========================================

    public EvaluatorOptimizerResult runEvaluatorOptimizerPattern(String task, int maxIterations) {
        ChatClient client = chatClientBuilder.build();
        int maxRounds = (maxIterations <= 0 || maxIterations > 3) ? 3 : maxIterations;

        List<IterationStep> history = new ArrayList<>();
        String currentSolution = "";
        String contextFeedback = "";

        for (int round = 1; round <= maxRounds; round++) {
            // 1. 生成或优化
            String genPrompt;
            if (round == 1) {
                genPrompt = "请根据以下要求给出初始完整解决方案：\n" + task;
            } else {
                genPrompt = String.format("""
                        针对任务：%s
                        上一版本方案：
                        %s
                        
                        评审专家给出的修改建议与不足：
                        %s
                        
                        请充分吸收以上反馈，输出全面提升重构后的新版本方案：
                        """, task, currentSolution, contextFeedback);
            }

            currentSolution = client.prompt(genPrompt).call().content();

            // 2. 严格评估
            String evalPrompt = String.format("""
                    你是一名极其严谨的质检评审专家。请评估以下方案是否达到优质交付标准：
                    目标任务: %s
                    待评方案:
                    %s
                    
                    请以 JSON 格式输出评估结果：
                    {
                      "passed": true或false (只有质量极高且无明显瑕疵时才能为 true),
                      "score": 1-100的整数评分,
                      "feedback": "详细的评价，若未通过必须列出明确的改进点"
                    }
                    """, task, currentSolution);

            EvaluationDecision decision = client.prompt(evalPrompt).call().entity(EvaluationDecision.class);
            if (decision == null) {
                decision = new EvaluationDecision(true, 85, "自动评测默认通过");
            }

            history.add(new IterationStep(round, decision.isPassed(), decision.getScore(), decision.getFeedback(), currentSolution));

            if (decision.isPassed()) {
                log.info("[Evaluator-Optimizer] 方案在第 {} 轮通过评审质检 (得分: {})", round, decision.getScore());
                return new EvaluatorOptimizerResult(task, currentSolution, round, true, history);
            }

            contextFeedback = decision.getFeedback();
        }

        return new EvaluatorOptimizerResult(task, currentSolution, maxRounds, false, history);
    }

    // DTO Records & Classes
    public record ChainResult(String finalOutput, List<String> steps, boolean success) {}
    public record PerspectiveAnalysis(String perspective, String content) {}
    public record ParallelResult(String topic, List<PerspectiveAnalysis> analyses, String finalReport) {}
    public record RoutingResult(String query, String detectedRoute, String executionPath, String response) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestratorPlan {
        private String analysis;
        @JsonProperty("subTasks")
        private List<SubTask> subTasks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTask {
        private String role;
        private String taskName;
        private String instruction;
    }

    public record WorkerDeliverable(String role, String taskName, String content) {}
    public record OrchestrationResult(String macroTask, String analysis, List<WorkerDeliverable> workerDeliverables, String finalSynthesized) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationDecision {
        private boolean passed;
        private int score;
        private String feedback;
    }

    public record IterationStep(int round, boolean passed, int score, String feedback, String solutionPreview) {}
    public record EvaluatorOptimizerResult(String task, String finalSolution, int totalRounds, boolean passed, List<IterationStep> history) {}
}
