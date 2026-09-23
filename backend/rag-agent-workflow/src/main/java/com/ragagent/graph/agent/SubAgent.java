package com.ragagent.graph.agent;

import com.ragagent.graph.GraphNode;
import com.ragagent.graph.GraphState;

/**
 * <h3>子智能体标准契约接口 (Sub-Agent Interface)</h3>
 * <p>
 * 遵循 Spring AI Alibaba Graph 规范，每一个子智能体都是有状态图（StateGraph）中的一个自闭环计算节点。
 * 子智能体接收当前的全局上下文 {@link GraphState}，利用大模型和专属工具完成特定专业领域的工作，
 * 并将结构化成果以不可变方式更新回状态流中。
 *
 * @author Java-RAG Team
 */
public interface SubAgent extends GraphNode {

    /**
     * 获取子智能体专属角色名称 (如 "PlannerAgent", "CoderExecutorAgent")
     */
    String getRoleName();

    /**
     * 获取子智能体专业职责描述
     */
    String getDescription();

    /**
     * 执行当前子智能体的领域逻辑
     *
     * @param state 当前图状态上下文
     * @return 经过本智能体推理、执行并更新后的图状态上下文
     */
    @Override
    GraphState execute(GraphState state);
}
