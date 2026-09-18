package com.ragagent.rag.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.graph.ConditionalEdge;
import com.ragagent.graph.GraphState;
import com.ragagent.graph.PostgresCheckpointer;
import com.ragagent.graph.StateGraph;
import com.ragagent.graph.nodes.CriticNode;
import com.ragagent.graph.nodes.HybridRetrievalNode;
import com.ragagent.graph.nodes.PlanNode;
import com.ragagent.graph.nodes.QueryRewriteNode;
import com.ragagent.graph.nodes.ReportNode;
import com.ragagent.rag.entity.AiResearchTask;
import com.ragagent.rag.mapper.AiResearchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepResearchService {

    private final AiResearchTaskMapper taskMapper;
    private final PostgresCheckpointer checkpointer;
    private final PlanNode planNode;
    private final HybridRetrievalNode retrievalNode;
    private final CriticNode criticNode;
    private final QueryRewriteNode queryRewriteNode;
    private final ReportNode reportNode;

    // 活跃任务进度监听器注册表 (用于 SSE 流式长连接实时推送)
    private final ConcurrentHashMap<String, Consumer<String>> stepListeners = new ConcurrentHashMap<>();
    // 任务执行历史事件日志缓存 (用于客户端晚连接时的状态回放与就绪补偿)
    private final ConcurrentHashMap<String, List<String>> taskLogsCache = new ConcurrentHashMap<>();

    public void registerListener(String taskId, Consumer<String> listener) {
        this.stepListeners.put(taskId, listener);

        // 1. 回放前面已产生的历史执行日志，杜绝时序差导致的消息丢失
        List<String> logs = taskLogsCache.get(taskId);
        if (logs != null && !logs.isEmpty()) {
            for (String logText : logs) {
                try {
                    listener.accept(logText);
                } catch (Exception ignored) {}
            }
        }

        // 2. 状态检查：如果任务已在数据库完成，立刻补发 REPORT_READY 终结通知，彻底根除无限转圈卡死
        AiResearchTask current = taskMapper.selectOne(new LambdaQueryWrapper<AiResearchTask>()
                .eq(AiResearchTask::getTaskId, taskId));
        if (current != null && ("COMPLETED".equals(current.getStatus()) || "FAILED".equals(current.getStatus()))) {
            try {
                listener.accept("REPORT_READY:" + taskId);
            } catch (Exception ignored) {}
        }
    }

    public void unregisterListener(String taskId) {
        this.stepListeners.remove(taskId);
    }

    public List<String> getTaskLogs(String taskId) {
        return this.taskLogsCache.getOrDefault(taskId, List.of());
    }

    /**
     * 删除深度研究任务
     */
    public boolean deleteTask(Long userId, String taskId) {
        this.stepListeners.remove(taskId);
        this.taskLogsCache.remove(taskId);
        return taskMapper.delete(new LambdaQueryWrapper<AiResearchTask>()
                .eq(AiResearchTask::getTaskId, taskId)
                .eq(AiResearchTask::getUserId, userId)) > 0;
    }

    /**
     * 提交深度研究任务
     */
    public AiResearchTask submitTask(Long userId, String topic) {
        String taskId = IdUtil.simpleUUID();
        AiResearchTask task = AiResearchTask.builder()
                .taskId(taskId)
                .userId(userId)
                .topic(topic)
                .status("PLANNING")
                .planSteps("[]")
                .currentStep(0)
                .reportMarkdown("")
                .citations("[]")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 初始化日志缓存
        taskLogsCache.put(taskId, new java.util.concurrent.CopyOnWriteArrayList<>());

        // 彻底脱离 Spring AOP 内部自调用失效陷阱，使用独立异步线程真正异步启动工作流
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                runResearchGraph(task);
            } catch (Exception e) {
                log.error("[DeepResearch] 异步任务执行异常: taskId={}", task.getTaskId(), e);
            }
        });

        // 主线程 5ms 立即响应前端请求，杜绝前端等待卡死
        return task;
    }

    public void runResearchGraph(AiResearchTask task) {
        try {
            // 构建生产级 Agentic RAG 有向循环状态机
            StateGraph graph = new StateGraph()
                    .withCheckpointer(checkpointer)
                    .addNode("PlanNode", planNode)
                    .addNode("RetrievalNode", retrievalNode)
                    .addNode("CriticNode", criticNode)
                    .addNode("QueryRewriteNode", queryRewriteNode)
                    .addNode("ReportNode", reportNode)
                    .setEntryPoint("PlanNode")
                    .addEdge("PlanNode", "RetrievalNode")
                    .addEdge("RetrievalNode", "CriticNode")
                    // 动态反思自愈条件路由：证据不足流转至 QueryRewrite，质检通过流转至 ReportNode
                    .addConditionalEdge("CriticNode", state -> {
                        String nextAction = (String) state.get("nextAction");
                        if ("REWRITE".equals(nextAction)) {
                            log.info("[StateGraph] CriticNode 判定证据不足，触发 Agentic RAG 自愈回路 -> QueryRewriteNode");
                            return "QueryRewriteNode";
                        }
                        log.info("[StateGraph] CriticNode 质检通过或触发兜底 -> ReportNode");
                        return "ReportNode";
                    })
                    // 自愈回路：问题重构后重新流转至 RetrievalNode 进行深搜
                    .addEdge("QueryRewriteNode", "RetrievalNode")
                    .addEdge("ReportNode", ConditionalEdge.END);

            GraphState initialState = GraphState.builder()
                    .threadId(task.getTaskId())
                    .topic(task.getTopic())
                    .build();

            Consumer<String> notifier = msg -> {
                // 缓存日志
                List<String> list = taskLogsCache.computeIfAbsent(task.getTaskId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>());
                list.add(msg);

                // 实时推送
                Consumer<String> l = stepListeners.get(task.getTaskId());
                if (l != null) l.accept(msg);
            };

            // 节点阶段产出物实时持久化监听器：每跑完一个节点立刻将大纲、切片证据写入数据库并广播
            java.util.function.BiConsumer<String, GraphState> nodeCompletedListener = (nodeName, state) -> {
                try {
                    boolean changed = false;
                    if ("PlanNode".equals(nodeName)) {
                        task.setStatus("RESEARCHING");
                        task.setCurrentStep(1);
                        task.setPlanSteps(JSONUtil.toJsonStr(state.getPlanSteps()));
                        changed = true;
                    } else if ("RetrievalNode".equals(nodeName)) {
                        task.setStatus("VERIFYING");
                        task.setCurrentStep(2);
                        task.setCitations(JSONUtil.toJsonStr(state.getEvidences()));
                        changed = true;
                    } else if ("CriticNode".equals(nodeName)) {
                        task.setStatus("RESEARCHING");
                        task.setCurrentStep(3);
                        changed = true;
                    } else if ("QueryRewriteNode".equals(nodeName)) {
                        task.setStatus("RESEARCHING");
                        task.setCurrentStep(1);
                        changed = true;
                    }
                    if (changed) {
                        task.setUpdateTime(LocalDateTime.now());
                        taskMapper.updateById(task);
                        log.info("[DeepResearch] 阶段产出物已实时持久化落库: node={}, taskId={}", nodeName, task.getTaskId());
                    }
                } catch (Exception ex) {
                    log.warn("[DeepResearch] 阶段产出物持久化异常: node={}", nodeName, ex);
                }
            };

            GraphState finalState = graph.invoke(initialState, notifier, nodeCompletedListener);

            // 更新任务状态与最终长篇研报
            task.setStatus("COMPLETED");
            task.setCurrentStep(4);
            task.setPlanSteps(JSONUtil.toJsonStr(finalState.getPlanSteps()));
            task.setReportMarkdown(finalState.getFinalReport());
            task.setCitations(JSONUtil.toJsonStr(finalState.getEvidences()));
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            notifier.accept("REPORT_READY:" + task.getTaskId());

        } catch (Exception e) {
            log.error("深度研究图工作流执行异常: taskId={}", task.getTaskId(), e);
            task.setStatus("FAILED");
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }
}
