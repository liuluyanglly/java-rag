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
@TableName("ai_chat_session")
@Schema(name = "AiChatSession", description = "多轮对话会话")
public class AiChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "自增主键ID", example = "1")
    private Long id;

    @Schema(description = "全局唯一会话UUID，后续流式对话与历史查询均以此为准", example = "3f2a9c1b7e4d4a10")
    private String sessionId;

    @Schema(description = "归属用户ID", example = "1")
    private Long userId;

    @Schema(description = "关联调用的智能体配置ID，为空表示使用默认通用助手", example = "1")
    private Long agentId;

    @Schema(description = "会话摘要标题，默认取首轮提问前 20 字，可由用户改名", example = "新对话")
    private String title;

    @Schema(description = "是否置顶固定在对话列表顶部", example = "false")
    private Boolean pinned;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "会话创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "该会话最后一次交互活跃时间，列表按此倒序", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "该会话累计消息条数")
    private Integer messageCount;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "最近一次用户提问内容")
    private String lastUserMessage;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "最近一次智能体回复内容")
    private String lastAssistantMessage;
}
