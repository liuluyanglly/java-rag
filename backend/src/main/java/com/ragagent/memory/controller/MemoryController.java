package com.ragagent.memory.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ragagent.common.result.Result;
import com.ragagent.memory.dto.ReflexionResult;
import com.ragagent.memory.entity.AiAgentMemory;
import com.ragagent.memory.service.LongTermMemoryService;
import com.ragagent.memory.service.MemoryEvolutionService;
import com.ragagent.memory.service.ShortTermMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Agent 记忆与自进化中心")
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final LongTermMemoryService longTermMemoryService;
    private final ShortTermMemoryService shortTermMemoryService;
    private final MemoryEvolutionService memoryEvolutionService;

    @Data
    @Schema(name = "MemoryUpdateRequest", description = "长期记忆修改入参")
    public static class MemoryUpdateRequest {

        @Schema(description = "修改后的记忆正文内容", example = "用户偏好简洁的要点式回答，不需要寒暄")
        private String content;

        @Schema(description = "修改后的重要性评分（1.0~10.0）", example = "8.5")
        private Double importance;
    }

    @Data
    @Schema(name = "MemoryCreateRequest", description = "手动添加长期记忆入参")
    public static class MemoryCreateRequest {

        @Schema(description = "归属智能体ID，可为空表示对全部智能体生效", example = "1")
        private Long agentId;

        @Schema(description = "来源会话UUID，可为空", example = "3f2a9c1b7e4d4a10")
        private String sessionId;

        @Schema(description = "记忆类型（SEMANTIC-事实语义, EPISODIC-情景事件, PREFERENCE-用户偏好, CORRECTION-自进化纠错规约）",
                example = "PREFERENCE", allowableValues = {"SEMANTIC", "EPISODIC", "PREFERENCE", "CORRECTION"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String memoryType;

        @Schema(description = "记忆正文内容", example = "用户偏好简洁的要点式回答，不需要寒暄",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String content;

        @Schema(description = "重要性评分（1.0~10.0），越高越优先被召回", example = "8.5")
        private Double importance;

        @Schema(description = "附加结构化元数据，JSON 字符串")
        private String metadata;
    }

    @Data
    @Schema(name = "FeedbackRequest", description = "对话反馈与纠错入参")
    public static class FeedbackRequest {

        @Schema(description = "被反馈的智能体ID", example = "1")
        private Long agentId;

        @Schema(description = "被反馈的会话UUID", example = "3f2a9c1b7e4d4a10")
        private String sessionId;

        @Schema(description = "被反馈那一轮的用户提问原文")
        private String query;

        @Schema(description = "被反馈那一轮的 Agent 回答原文")
        private String answer;

        @Schema(description = "满意度评分（1-点赞, -1-点踩）。仅为负值时才触发 Reflexion 自省并沉淀纠错规约",
                example = "-1", allowableValues = {"1", "-1"})
        private Integer rating;

        @Schema(description = "纠错指正意见，负向反馈时作为归因诊断的关键输入",
                example = "回答遗漏了灰度发布环节，且引用的文档版本过旧")
        private String comment;
    }

    @Operation(summary = "查询当前用户的长期记忆列表",
            description = "按记忆类型可选过滤，出参中 score 字段为空（仅检索接口返回相关度）")
    @GetMapping("/list")
    public Result<List<AiAgentMemory>> listMemories(
            @Parameter(description = "记忆类型过滤，为空则返回全部类型", example = "PREFERENCE",
                    schema = @Schema(allowableValues = {"SEMANTIC", "EPISODIC", "PREFERENCE", "CORRECTION"}))
            @RequestParam(name = "memoryType", required = false) String memoryType,
            org.springframework.web.server.ServerWebExchange exchange) {
        Long userId = com.ragagent.common.security.SecurityUtils.getLoginUserIdOrDefault(1L);
        List<AiAgentMemory> list = longTermMemoryService.getUserMemories(userId, memoryType);
        return Result.success(list);
    }

    @Operation(summary = "检索与指定内容相关的长期记忆",
            description = "按语义相关度召回，出参 score 字段为本次计算的综合相关度评分")
    @GetMapping("/search")
    public Result<List<AiAgentMemory>> searchMemories(
            @Parameter(description = "检索文本", example = "回答风格偏好", required = true)
            @RequestParam(name = "query") String query,
            @Parameter(description = "返回条数上限", example = "5")
            @RequestParam(name = "topK", defaultValue = "5") int topK,
            org.springframework.web.server.ServerWebExchange exchange) {
        Long userId = com.ragagent.common.security.SecurityUtils.getLoginUserIdOrDefault(1L);
        List<AiAgentMemory> list = longTermMemoryService.searchMemories(userId, query, topK);
        return Result.success(list);
    }

    @Operation(summary = "手动添加长期记忆", description = "出参 data 为落库后的完整记忆条目")
    @PostMapping("/add")
    public Result<AiAgentMemory> addMemory(@RequestBody MemoryCreateRequest req, org.springframework.web.server.ServerWebExchange exchange) {
        Long userId = com.ragagent.common.security.SecurityUtils.getLoginUserIdOrDefault(1L);
        AiAgentMemory mem = longTermMemoryService.addMemory(
                userId,
                req.getAgentId(),
                req.getSessionId(),
                req.getMemoryType(),
                req.getContent(),
                req.getImportance(),
                req.getMetadata()
        );
        return Result.success(mem);
    }

    @Operation(summary = "修改长期记忆", description = "出参 data 为是否修改成功")
    @PutMapping("/{id}")
    public Result<Boolean> updateMemory(
            @Parameter(description = "记忆条目ID", example = "1838294857392746496", required = true)
            @PathVariable String id,
            @RequestBody MemoryUpdateRequest req) {
        boolean ok = longTermMemoryService.updateMemory(id, req.getContent(), req.getImportance());
        return Result.success(ok);
    }

    @Operation(summary = "删除长期记忆 (支持用户数据自主权)", description = "出参 data 为是否删除成功")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteMemory(
            @Parameter(description = "记忆条目ID", example = "1838294857392746496", required = true)
            @PathVariable String id,
            org.springframework.web.server.ServerWebExchange exchange) {
        boolean ok = longTermMemoryService.deleteMemory(id);
        return Result.success(ok);
    }

    @Operation(summary = "清除会话短期记忆缓存",
            description = "清空该会话在 Redis 中的多轮上下文，不影响数据库消息记录与长期记忆")
    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> clearSessionMemory(
            @Parameter(description = "会话UUID", example = "3f2a9c1b7e4d4a10", required = true)
            @PathVariable String sessionId) {
        shortTermMemoryService.clearSession(sessionId);
        return Result.success(true);
    }

    @Operation(summary = "提交用户反馈并触发反射演化",
            description = "rating 为负值时触发 Reflexion 归因诊断并沉淀防再犯规约，出参 data 为自省结果；"
                    + "正向反馈仅记录，data 返回 null")
    @PostMapping("/feedback")
    public Result<ReflexionResult> submitFeedback(@RequestBody FeedbackRequest req, org.springframework.web.server.ServerWebExchange exchange) {
        Long userId = com.ragagent.common.security.SecurityUtils.getLoginUserIdOrDefault(1L);
        // 如果是负向反馈(点踩或有纠错批注)，触发 Reflexion 纠错反省并沉淀防再犯规约
        if (req.getRating() != null && req.getRating() < 0) {
            ReflexionResult reflexion = memoryEvolutionService.reflectAndEvolve(
                    userId,
                    req.getAgentId(),
                    req.getSessionId(),
                    req.getQuery(),
                    req.getAnswer(),
                    req.getComment()
            );
            return Result.success(reflexion);
        }
        return Result.success(null);
    }
}
