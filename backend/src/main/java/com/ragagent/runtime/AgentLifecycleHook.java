package com.ragagent.runtime;

/**
 * MateClaw Agent 运行时生命周期拦截钩子
 */
public interface AgentLifecycleHook {

    default void onStateTransition(AgentContext context, AgentState from, AgentState to) {}

    default void beforeToolExecution(AgentContext context, String toolName, Object args) {}

    default void afterToolExecution(AgentContext context, String toolName, Object result, long costMs) {}

    default void onError(AgentContext context, Throwable throwable) {}
}
