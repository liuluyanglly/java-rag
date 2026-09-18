package com.ragagent.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_agent_memory")
@Schema(name = "AiAgentMemory", description = "Agent 长期记忆条目")
public class AiAgentMemory implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "记忆条目ID，雪花算法字符串主键", example = "1838294857392746496")
    private String id;

    @Schema(description = "归属用户ID", example = "1")
    private Long userId;

    @Schema(description = "产生该记忆的智能体ID，可为空", example = "1")
    private Long agentId;

    @Schema(description = "产生该记忆的会话UUID，可为空", example = "3f2a9c1b7e4d4a10")
    private String sessionId;

    /**
     * 记忆类型: SEMANTIC(事实语义), EPISODIC(情景事件), PREFERENCE(用户偏好), CORRECTION(自进化纠错)
     */
    @Schema(description = "记忆类型（SEMANTIC-事实语义, EPISODIC-情景事件, PREFERENCE-用户偏好, CORRECTION-自进化纠错规约）",
            example = "PREFERENCE", allowableValues = {"SEMANTIC", "EPISODIC", "PREFERENCE", "CORRECTION"})
    private String memoryType;

    @Schema(description = "记忆正文内容", example = "用户偏好简洁的要点式回答，不需要寒暄")
    private String content;

    /**
     * 重要性评分 (1.0 ~ 10.0)
     */
    @Schema(description = "重要性评分（1.0~10.0），越高越优先被召回注入上下文；纠错类规约通常为 9.0~10.0",
            example = "8.5")
    private Double importance;

    /**
     * 召回强化次数 (艾宾浩斯强化)
     */
    @Schema(description = "被成功召回的累计次数，用于艾宾浩斯遗忘曲线强化", example = "3")
    private Integer accessCount;

    @Schema(description = "附加结构化元数据，JSON 字符串")
    private String metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "记忆沉淀创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "记忆最后修改时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "最近一次被召回的时间", example = "2026-09-16 14:47:05")
    private LocalDateTime lastAccessedTime;

    /**
     * 动态计算的综合召回相关度评分
     */
    @TableField(exist = false)
    @Schema(description = "本次检索动态计算的综合召回相关度评分，仅检索接口出参携带", example = "0.87")
    private Double score;
}
