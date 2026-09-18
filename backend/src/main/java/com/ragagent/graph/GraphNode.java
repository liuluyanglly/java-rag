package com.ragagent.graph;

/**
 * 状态图节点抽象接口
 */
@FunctionalInterface
public interface GraphNode {
    GraphState execute(GraphState state);
}
