package com.ragagent.graph;

/**
 * 状态图动态条件边接口
 */
@FunctionalInterface
public interface ConditionalEdge {
    String END = "__END__";

    /**
     * 根据当前状态决定流转目标节点名
     */
    String route(GraphState state);
}
