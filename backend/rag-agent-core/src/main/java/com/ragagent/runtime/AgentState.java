package com.ragagent.runtime;

/**
 * MateClaw Agent 状态机标准化生命周期枚举
 */
public enum AgentState {
    INIT,        // 初始化上下文与提示词装配
    PLAN,        // 意图分析与目标分解规划
    DISPATCH,    // 工具路由与分发
    ACT,         // 工具调用与外部动作执行
    OBSERVE,     // 结果捕获与环境观察
    REFLECT,     // 思考反思与自我修正
    SUSPEND,     // 等待人机协同干预 (Human-in-the-Loop)
    FINISH,      // 最终回答生成与归档
    FAILED       // 异常终态
}
