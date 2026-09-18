package com.ragagent.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mem0 记忆更新决策结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDecisionResult implements Serializable {

    /**
     * 决策动作: ADD (新增), UPDATE (更新), DELETE (遗忘废弃), NOOP (无变化/忽略)
     */
    private String action;

    /**
     * 目标记忆ID (UPDATE / DELETE 时必填)
     */
    private String targetMemoryId;

    /**
     * 记忆分类: SEMANTIC(事实语义), PREFERENCE(用户偏好), EPISODIC(事件情景), CORRECTION(自进化纠错)
     */
    private String memoryType;

    /**
     * 提炼出的记忆核心内容
     */
    private String content;

    /**
     * 重要度评分 (1.0 ~ 10.0)
     */
    private Double importance;

    /**
     * 决策判定理由与因果分析
     */
    private String rationale;
}
