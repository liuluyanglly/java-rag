package com.ragagent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("ai_research_task")
@Schema(name = "AiResearchTask", description = "深度研究任务与研报产出")
public class AiResearchTask implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "自增主键ID", example = "1")
    private Long id;

    @Schema(description = "任务全局唯一UUID，用于订阅 SSE 进度流与查询详情", example = "a1b2c3d4e5f6")
    private String taskId;

    @Schema(description = "发起人用户ID", example = "1")
    private Long userId;

    @Schema(description = "研究课题原文", example = "国内企业级 RAG 平台的技术选型与落地路径")
    private String topic;

    /**
     * 状态: PLANNING(大纲规划中), RESEARCHING(多步并发调研中), VERIFYING(事实交叉核验), COMPLETED(报告已完成), FAILED(失败)
     */
    @Schema(description = "工作流状态（PLANNING-大纲规划中, RESEARCHING-多步并发调研中, VERIFYING-事实交叉核验, COMPLETED-报告已完成, FAILED-失败）",
            example = "COMPLETED", allowableValues = {"PLANNING", "RESEARCHING", "VERIFYING", "COMPLETED", "FAILED"})
    private String status;

    /**
     * 拆解出的研究步骤 (JSON 数组)
     */
    @Schema(description = "由 PlanNode 拆解出的研究步骤清单，JSON 数组字符串",
            example = "[\"梳理主流技术栈\",\"对比检索召回策略\",\"总结落地风险\"]")
    private String planSteps;

    @Schema(description = "当前已执行到的步骤序号，从 0 开始", example = "3")
    private Integer currentStep;

    /**
     * 最终长篇结构化研报 Markdown
     */
    @Schema(description = "最终长篇结构化研报正文，Markdown 格式。仅 status=COMPLETED 时有值")
    private String reportMarkdown;

    /**
     * 引用来源列表 (JSON 数组)
     */
    @Schema(description = "研报引用来源列表，JSON 数组字符串")
    private String citations;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "任务提交时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "状态最后流转时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;
}
