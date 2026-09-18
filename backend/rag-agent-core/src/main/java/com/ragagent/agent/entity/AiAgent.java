package com.ragagent.agent.entity;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_agent")
@Schema(name = "AiAgent", description = "AI 智能体模型编排配置")
public class AiAgent implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "智能体ID，自增主键。新增时无需传入", example = "1")
    private Long id;

    @Schema(description = "智能体展示名称", example = "研发规范助手")
    private String name;

    @Schema(description = "智能体功能定位说明")
    private String description;

    @Schema(description = "智能体头像URL")
    private String avatar;

    @Schema(description = "底层调用的大模型标识", example = "deepseek-chat")
    private String modelName;

    @Schema(description = "智能体系统提示词人设模板（System Prompt）",
            example = "你是一个严谨的企业研发规范顾问，回答须严格依据检索到的知识切片。")
    private String systemPrompt;

    @Schema(description = "LLM 生成发散温度参数（0.00~1.00），越大越发散", example = "0.70")
    private BigDecimal temperature;

    @Schema(description = "单次回复生成的最大 Token 输出阈值", example = "4096")
    private Integer maxTokens;

    /**
     * 绑定的知识库ID数组 (JSON 字符串)
     */
    @Schema(description = "挂载关联的知识库ID集合，JSON 数组字符串", example = "[1, 2]")
    private String datasetIds;

    /**
     * 启用的工具列表 (JSON 字符串)
     */
    @Schema(description = "启用的工具/MCP 插件名称集合，JSON 数组字符串", example = "[\"knowledgeSearch\"]")
    private String tools;

    @Schema(description = "是否公开可用（true-全员公开, false-私有私享）", example = "true")
    private Boolean isPublic;

    @Schema(description = "智能体服务状态（0-正常, 1-停用）", example = "0", allowableValues = {"0", "1"})
    private String status;

    @Schema(description = "创建人用户ID，由服务端从当前登录态写入", example = "1")
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改配置时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;
}
