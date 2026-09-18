package com.ragagent.graph;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Spring AI Alibaba Graph 风格的 StateGraph 有状态图工作流编排引擎
 */
@Slf4j
public class StateGraph {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<String, String> edges = new HashMap<>();
    private final Map<String, ConditionalEdge> conditionalEdges = new HashMap<>();
    private String entryPoint;
    private PostgresCheckpointer checkpointer;

    public StateGraph addNode(String name, GraphNode node) {
        this.nodes.put(name, node);
        return this;
    }

    public StateGraph addEdge(String from, String to) {
        this.edges.put(from, to);
        return this;
    }

    public StateGraph addConditionalEdge(String from, ConditionalEdge edge) {
        this.conditionalEdges.put(from, edge);
        return this;
    }

    public StateGraph setEntryPoint(String entryNode) {
        this.entryPoint = entryNode;
        return this;
    }

    public StateGraph withCheckpointer(PostgresCheckpointer checkpointer) {
        this.checkpointer = checkpointer;
        return this;
    }

    public GraphState invoke(GraphState initialState, Consumer<String> stepNotifier) {
        return invoke(initialState, stepNotifier, null);
    }

    /**
     * 编译并执行图流转（支持节点执行完成阶段性状态回调）
     */
    public GraphState invoke(GraphState initialState, Consumer<String> stepNotifier, java.util.function.BiConsumer<String, GraphState> nodeCompletedListener) {
        if (entryPoint == null || !nodes.containsKey(entryPoint)) {
            throw new IllegalStateException("图工作流未指定合法入口节点 (entryPoint)");
        }

        String currentNodeName = entryPoint;
        GraphState currentState = initialState;
        int maxIterations = 20; // 防止无限条件环流
        int iteration = 0;

        log.info("StateGraph 开始流转: threadId={}, 入口节点={}", currentState.getThreadId(), currentNodeName);

        while (currentNodeName != null && !ConditionalEdge.END.equals(currentNodeName) && iteration < maxIterations) {
            iteration++;
            currentState.setCurrentNode(currentNodeName);
            if (stepNotifier != null) {
                stepNotifier.accept("▶ 节点流转: [" + currentNodeName + "]");
            }

            GraphNode node = nodes.get(currentNodeName);
            if (node == null) {
                log.error("未找到对应节点处理器: {}", currentNodeName);
                break;
            }

            log.info("执行节点: [{}]", currentNodeName);
            currentState = node.execute(currentState);

            // 实时触发节点完成回调（用于向数据库与前端实时同步产出）
            if (nodeCompletedListener != null) {
                try {
                    nodeCompletedListener.accept(currentNodeName, currentState);
                } catch (Exception ex) {
                    log.warn("节点完成监听回调异常: node={}", currentNodeName, ex);
                }
            }

            // 检查点持久化
            if (checkpointer != null && currentState.getThreadId() != null) {
                checkpointer.saveCheckpoint(currentState.getThreadId(), currentNodeName, currentState);
            }

            // 计算下一跳节点
            if (conditionalEdges.containsKey(currentNodeName)) {
                ConditionalEdge cEdge = conditionalEdges.get(currentNodeName);
                currentNodeName = cEdge.route(currentState);
            } else if (edges.containsKey(currentNodeName)) {
                currentNodeName = edges.get(currentNodeName);
            } else {
                // 没有后续边，终结
                currentNodeName = ConditionalEdge.END;
            }
        }

        log.info("StateGraph 流转完成: threadId={}, 耗费轮次={}", currentState.getThreadId(), iteration);
        return currentState;
    }
}
