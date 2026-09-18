package com.ragagent.graph;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * <h1>Spring AI Alibaba Graph 风格的 StateGraph 有状态图工作流编排引擎</h1>
 * <p>
 * 本类是多智能体复杂协同（Multi-Agent Collaboration）的核心调度运行时。
 * 与传统的线性链式调用（Chain / Pipeline）不同，<b>StateGraph</b> 具备以下四大关键特性：
 * <ol>
 *   <li><b>循环与自愈回路 (Loops & Self-Correction)</b>：
 *       <br>支持条件边（{@link ConditionalEdge}）根据当前状态动态路由，形成“检索 -> 评估 -> 不合格打回重写 -> 二次深搜”的闭环反馈流。</li>
 *   <li><b>统一不可变状态流转 (Stateful Context)</b>：
 *       <br>图的所有节点共享 {@link GraphState}，每个节点执行纯函数式的状态迁移（Input State -> Output State），避免全局变量污染。</li>
 *   <li><b>检查点与断点续流 (Checkpointer & Time Travel)</b>：
 *       <br>每执行完一个节点，自动将状态快照持久化至数据库。支持任务宕机恢复或回滚重放。</li>
 *   <li><b>环路安全熔断 (Circuit Breaker)</b>：
 *       <br>内置 {@code maxIterations} 阈值限制，防止智能体在反思与重搜之间发生无限死循环。</li>
 * </ol>
 *
 * @author Java-RAG Team
 * @see GraphNode
 * @see ConditionalEdge
 * @see GraphState
 */
@Slf4j
public class StateGraph {

    /** 注册的节点映射表 (NodeName -> GraphNode 实现类) */
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    /** 固定无条件跳转边映射表 (FromNode -> ToNode) */
    private final Map<String, String> edges = new HashMap<>();
    /** 动态条件路由边映射表 (FromNode -> ConditionalEdge 路由策略) */
    private final Map<String, ConditionalEdge> conditionalEdges = new HashMap<>();
    /** 工作流的初始入口节点名称 */
    private String entryPoint;
    /** 状态检查点持久化存储器 (可选) */
    private PostgresCheckpointer checkpointer;

    /**
     * <h3>向状态图注册一个计算节点</h3>
     *
     * @param name 节点唯一标识名（如 PlanNode、CriticNode）
     * @param node 节点业务逻辑实现类
     * @return 当前 StateGraph 实例 (链式调用模式)
     */
    public StateGraph addNode(String name, GraphNode node) {
        this.nodes.put(name, node);
        return this;
    }

    /**
     * <h3>添加一条确定的顺序流转边 (Fixed Edge)</h3>
     * <p>当 source 节点执行完成后，无条件跳转至 target 节点。</p>
     *
     * @param from 源节点名称
     * @param to   目标下一跳节点名称
     * @return 当前 StateGraph 实例
     */
    public StateGraph addEdge(String from, String to) {
        this.edges.put(from, to);
        return this;
    }

    /**
     * <h3>添加一条动态条件决策边 (Conditional Edge)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * 节点执行完毕后，执行传入的 {@link ConditionalEdge#route(GraphState)} 判定函数，
     * 依据当前状态动态计算下一跳是继续执行、打回重写回路，还是流向结束 {@code ConditionalEdge.END}。
     *
     * @param from 源节点名称
     * @param edge 条件路由决策器
     * @return 当前 StateGraph 实例
     */
    public StateGraph addConditionalEdge(String from, ConditionalEdge edge) {
        this.conditionalEdges.put(from, edge);
        return this;
    }

    /**
     * <h3>配置工作流拓扑图的起始入口节点 (Entry Point)</h3>
     *
     * @param entryNode 入口节点名称
     * @return 当前 StateGraph 实例
     */
    public StateGraph setEntryPoint(String entryNode) {
        this.entryPoint = entryNode;
        return this;
    }

    /**
     * <h3>装配检查点持久化存储器</h3>
     *
     * @param checkpointer 数据库持久化实现
     * @return 当前 StateGraph 实例
     */
    public StateGraph withCheckpointer(PostgresCheckpointer checkpointer) {
        this.checkpointer = checkpointer;
        return this;
    }

    /**
     * <h3>同步执行工作流（简易通知重载）</h3>
     *
     * @param initialState 初始状态
     * @param stepNotifier 阶段事件文本通知回调 (如 SSE 实时推流给前端)
     * @return 最终产出的状态
     */
    public GraphState invoke(GraphState initialState, Consumer<String> stepNotifier) {
        return invoke(initialState, stepNotifier, null);
    }

    /**
     * <h3>编译并驱动状态机运转全流程 (核心驱动执行器)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li><b>状态机循环主循环：</b> 从 {@code entryPoint} 开始，循环获取当前节点、执行逻辑、触发实时完成回调、持久化检查点快照。</li>
     *   <li><b>下一跳路由解析：</b> 优先检查条件边（{@code conditionalEdges}），若无条件边则匹配固定边（{@code edges}），若均无则视为自然终止。</li>
     *   <li><b>熔断保护机制：</b> 当由于反思失败反复重试超过 {@code maxIterations=20} 次时，自动强制终止，保障系统算力与 Token 资产安全。</li>
     * </ul>
     *
     * @param initialState          初始图状态对象
     * @param stepNotifier          步骤通知器（向前端或日志输出阶段文本）
     * @param nodeCompletedListener 节点完成监听器（(nodeName, state) -> 实时将阶段成果如大纲、引用切片同步回库）
     * @return 图执行完毕后的最终状态对象
     */
    public GraphState invoke(GraphState initialState, Consumer<String> stepNotifier, java.util.function.BiConsumer<String, GraphState> nodeCompletedListener) {
        // 步骤 1：合法性检验
        if (entryPoint == null || !nodes.containsKey(entryPoint)) {
            throw new IllegalStateException("图工作流未指定合法入口节点 (entryPoint: " + entryPoint + ")");
        }

        String currentNodeName = entryPoint;
        GraphState currentState = initialState;
        int maxIterations = 20; // 最大迭代上限，防止无限循环
        int iteration = 0;

        log.info("【StateGraph】开始驱动状态图执行: threadId={}, 入口节点={}", currentState.getThreadId(), currentNodeName);

        // 步骤 2：状态机循环流转
        while (currentNodeName != null && !ConditionalEdge.END.equals(currentNodeName) && iteration < maxIterations) {
            iteration++;
            currentState.setCurrentNode(currentNodeName);
            if (stepNotifier != null) {
                stepNotifier.accept("▶ 节点流转: [" + currentNodeName + "]");
            }

            // 提取当前待执行的节点处理器
            GraphNode node = nodes.get(currentNodeName);
            if (node == null) {
                log.error("【StateGraph】未找到对应节点处理器: {}", currentNodeName);
                break;
            }

            log.info("【StateGraph】正在执行节点: [{}] (第 {} 轮流转)", currentNodeName, iteration);
            // 执行节点核心业务逻辑并产出新状态
            currentState = node.execute(currentState);

            // 步骤 3：实时触发阶段完成回调（用于向数据库与前端前端实时同步中间产物）
            if (nodeCompletedListener != null) {
                try {
                    nodeCompletedListener.accept(currentNodeName, currentState);
                } catch (Exception ex) {
                    log.warn("【StateGraph】节点完成监听回调异常: node={}", currentNodeName, ex);
                }
            }

            // 步骤 4：持久化检查点快照 (Checkpointer)
            if (checkpointer != null && currentState.getThreadId() != null) {
                checkpointer.saveCheckpoint(currentState.getThreadId(), currentNodeName, currentState);
            }

            // 步骤 5：动态计算下一跳路由节点
            if (conditionalEdges.containsKey(currentNodeName)) {
                // 条件边决策
                ConditionalEdge cEdge = conditionalEdges.get(currentNodeName);
                currentNodeName = cEdge.route(currentState);
            } else if (edges.containsKey(currentNodeName)) {
                // 固定边跳转
                currentNodeName = edges.get(currentNodeName);
            } else {
                // 无后继节点，正常结束
                currentNodeName = ConditionalEdge.END;
            }
        }

        // 步骤 6：超限熔断警告
        if (iteration >= maxIterations) {
            log.warn("【StateGraph】达到最大安全迭代步数限制 ({})，已触发熔断终止！", maxIterations);
        }

        log.info("【StateGraph】图工作流流转完成: threadId={}, 最终节点={}", currentState.getThreadId(), currentNodeName);
        return currentState;
    }
}
