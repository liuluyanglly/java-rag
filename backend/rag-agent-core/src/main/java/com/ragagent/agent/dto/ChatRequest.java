package com.ragagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "ChatRequest", description = "对话请求体")
public class ChatRequest {

    @Schema(description = "会话UUID，为空时服务端自动新建会话并在完成时返回", example = "3f2a9c1b7e4d4a10")
    private String sessionId;

    @Schema(description = "绑定的智能体ID，未传使用系统默认配置", example = "1")
    private Long agentId;

    @Schema(description = "用户输入的提示词内容", example = "你好，请介绍一下你自己", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "临时挂载/追加的知识库ID列表，与智能体配置取并集", example = "[1, 2]")
    private List<Long> datasetIds;

    @Schema(description = "采样温度（0.0~2.0），越高随机性越强，空则遵循智能体默认值", example = "0.7")
    private Double temperature;

    @Schema(description = "知识库检索 Top-K，空则默认 5", example = "5")
    private Integer topK;

    @Schema(description = "相似度分数过滤阈值（0.0~1.0），空则默认 0.3", example = "0.3")
    private Double scoreThreshold;

    @Schema(description = "是否开启深度思考与意图推演", example = "true")
    private Boolean enableThinking;
}
