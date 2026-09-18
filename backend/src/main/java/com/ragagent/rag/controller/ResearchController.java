package com.ragagent.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.common.result.Result;
import com.ragagent.common.security.SecurityUtils;
import com.ragagent.rag.entity.AiResearchTask;
import com.ragagent.rag.mapper.AiResearchTaskMapper;
import com.ragagent.rag.service.DeepResearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Tag(name = "深度研究与研报工坊 (Graph 状态机)")
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchController {

    private final DeepResearchService deepResearchService;
    private final AiResearchTaskMapper taskMapper;

    @Data
    @Schema(name = "SubmitTopicRequest", description = "提交深度研究课题入参")
    public static class SubmitTopicRequest {

        @Schema(description = "研究课题，支持开放式提问或复杂技术调研主题",
                example = "基于 Spring AI Alibaba 与 pgvector 的企业级 RAG 架构落地最佳实践",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String topic;
    }

    @Operation(summary = "提交深度研究课题",
            description = "启动 StateGraph 多 Agent 工作流并立即返回任务记录（status=PLANNING）。"
                    + "取出参 data.taskId 后订阅 /api/research/stream/{taskId} 观察实时进度")
    @PostMapping("/submit")
    public Result<AiResearchTask> submit(@RequestBody SubmitTopicRequest req, ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        AiResearchTask task = deepResearchService.submitTask(userId, req.getTopic());
        return Result.success("深度研究工作流已启动", task);
    }

    @Operation(summary = "获取当前用户的研究任务列表",
            description = "按提交时间倒序返回。列表项含 reportMarkdown 全文，仅需概览时可只读取 status 与 topic")
    @GetMapping("/tasks")
    public Result<List<AiResearchTask>> getTasks(ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        List<AiResearchTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiResearchTask>()
                .eq(AiResearchTask::getUserId, userId)
                .orderByDesc(AiResearchTask::getCreateTime));
        return Result.success(tasks);
    }

    @Operation(summary = "获取任务详情与 Markdown 研报",
            description = "status=COMPLETED 时 reportMarkdown 字段为最终长篇研报正文")
    @GetMapping("/task/{taskId}")
    public Result<AiResearchTask> getTaskDetail(
            @Parameter(description = "任务UUID，来自提交接口返回的 data.taskId",
                    example = "a1b2c3d4e5f6", required = true)
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        AiResearchTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiResearchTask>()
                .eq(AiResearchTask::getTaskId, taskId));
        return Result.success(task);
    }

    @Operation(summary = "SSE 实时订阅研究进度流",
            description = """
                    以 `text/event-stream` 推送 StateGraph 节点流转进度，事件名恒为 `progress`，
                    data 为进度描述文本。当收到以 `REPORT_READY` 开头的消息时研报已生成，
                    服务端随即关闭连接，此时改调 /api/research/task/{taskId} 获取研报全文。

                    注意：Scalar 的在线调试不渲染流式响应，建议用 curl 或前端联调验证。
                    """)
    @ApiResponse(responseCode = "200", description = "SSE 进度事件流，直至 REPORT_READY 后关闭",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string", description = "进度描述文本")))
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamProgress(
            @Parameter(description = "任务UUID", example = "a1b2c3d4e5f6", required = true)
            @PathVariable String taskId) {
        return Flux.create(sink -> {
            deepResearchService.registerListener(taskId, msg -> {
                sink.next(ServerSentEvent.<String>builder().event("progress").data(msg).build());
                if (msg.startsWith("REPORT_READY")) {
                    sink.complete();
                }
            });
            sink.onDispose(() -> deepResearchService.unregisterListener(taskId));
        });
    }

    @Operation(summary = "删除研究任务记录", description = "级联清理状态图缓存与任务数据")
    @DeleteMapping("/task/{taskId}")
    public Result<Boolean> deleteTask(@PathVariable String taskId, ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserIdOrDefault(1L);
        boolean ok = deepResearchService.deleteTask(userId, taskId);
        return Result.success("删除成功", ok);
    }
}
