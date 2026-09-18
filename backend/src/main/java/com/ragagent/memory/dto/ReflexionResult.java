package com.ragagent.memory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Reflexion 反思与自我进化结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReflexionResult", description = "Reflexion 反思与自我进化结果，仅负向反馈触发时返回，正向反馈返回 null")
public class ReflexionResult implements Serializable {

    /**
     * 原始提问
     */
    @Schema(description = "触发反思的原始用户提问")
    private String query;

    /**
     * Agent 原回答摘要
     */
    @Schema(description = "Agent 原回答摘要")
    private String originalAnswer;

    /**
     * 用户反馈意见 / 纠错指正
     */
    @Schema(description = "用户提交的反馈意见与纠错指正")
    private String userFeedback;

    /**
     * 错误归因诊断 (为何产生不佳回答)
     */
    @Schema(description = "错误归因诊断，说明本次回答不佳的根本原因")
    private String errorDiagnosis;

    /**
     * 沉淀的自我改进准则 (面向未来的防再犯规则)
     */
    @Schema(description = "沉淀出的自我改进准则，将作为 CORRECTION 类长期记忆注入后续对话以防再犯")
    private String improvementRule;

    /**
     * 严重程度/重要度 (1.0 ~ 10.0，通常纠错规则为 9.0 ~ 10.0)
     */
    @Schema(description = "严重程度与重要度（1.0~10.0），纠错规则通常为 9.0~10.0", example = "9.5")
    private Double importance;
}
