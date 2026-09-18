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
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_message")
@Schema(name = "AiChatMessage", description = "对话流单条消息明细")
public class AiChatMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "消息唯一自增ID", example = "1")
    private Long id;

    @Schema(description = "归属会话UUID", example = "3f2a9c1b7e4d4a10")
    private String sessionId;

    /**
     * 角色: user, assistant, system, tool
     */
    @Schema(description = "发信角色（user-用户, assistant-大模型助手, system-系统, tool-工具调用返回）",
            example = "assistant", allowableValues = {"user", "assistant", "system", "tool"})
    private String role;

    @Schema(description = "消息正文文本内容")
    private String content;

    /**
     * 思考过程 (DeepSeek-R1 / OpenAI o1 思维链)
     */
    @Schema(description = "深度思考推理链明细（DeepSeek-R1 思维链或 OpenAI 推理记录）")
    private String thought;

    @Schema(description = "本条消息消耗的 Token 量统计", example = "256")
    private Integer tokens;

    /**
     * 工具调用 JSON 记录
     */
    @Schema(description = "工具调用入参与出参轨迹记录，JSON 字符串")
    private String toolCalls;

    /**
     * 溯源引用 JSON 记录
     */
    @Schema(description = "知识库 RAG 检索溯源切片引用详情，JSON 数组字符串；无命中时为 []", example = "[]")
    private String citations;

    @Schema(description = "用户满意度打分（like-点赞, dislike-点踩）", allowableValues = {"like", "dislike"})
    private String feedback;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "消息生成落库时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;
}
