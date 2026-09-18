package com.ragagent.agent.controller;

import com.ragagent.agent.service.PromptFileService;
import com.ragagent.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 基于文件的提示词模板管理中心 (Prompt File Management)
 * 提供提示词模板的查看、参数探测、动态渲染及热重载能力
 */
@Slf4j
@Tag(name = "Prompt 提示词文件管理中心")
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptFileService promptFileService;

    @Operation(summary = "获取所有提示词文件模板列表", description = "返回预置及扩展的 .st 模板清单与支持的占位参数")
    @GetMapping
    public Result<List<PromptFileService.PromptTemplateInfo>> listTemplates() {
        return Result.success(promptFileService.listTemplates());
    }

    @Operation(summary = "获取指定提示词模板详情", description = "根据 templateId 获取包含原始文件内容的模板对象")
    @GetMapping("/{templateId}")
    public Result<PromptFileService.PromptTemplateInfo> getTemplate(
            @Parameter(description = "模板ID", example = "system-default", required = true)
            @PathVariable String templateId) {
        return promptFileService.getTemplate(templateId)
                .map(Result::success)
                .orElseGet(() -> Result.error("未找到指定的提示词模板: " + templateId));
    }

    @Data
    @Schema(name = "PromptRenderRequest", description = "提示词动态渲染请求")
    public static class PromptRenderRequest {
        @Schema(description = "模板ID", example = "system-default", requiredMode = Schema.RequiredMode.REQUIRED)
        private String templateId;

        @Schema(description = "占位符替换变量表", example = "{\"currentTime\": \"2026-09-18 10:00:00\", \"dayOfWeek\": \"星期五\"}")
        private Map<String, Object> variables;
    }

    @Operation(summary = "动态预览与渲染提示词", description = "基于 Spring AI 原生 PromptTemplate 结合变量完成占位符动态填充")
    @PostMapping("/render")
    public Result<String> renderTemplate(@RequestBody PromptRenderRequest req) {
        try {
            String rendered = promptFileService.render(req.getTemplateId(), req.getVariables());
            return Result.success("渲染成功", rendered);
        } catch (Exception e) {
            return Result.error("渲染失败: " + e.getMessage());
        }
    }

    @Data
    @Schema(name = "PromptSaveRequest", description = "保存或新增提示词文件请求")
    public static class PromptSaveRequest {
        @Schema(description = "模板ID", example = "custom-translator", requiredMode = Schema.RequiredMode.REQUIRED)
        private String templateId;

        @Schema(description = "模板纯文本内容", requiredMode = Schema.RequiredMode.REQUIRED)
        private String content;
    }

    @Operation(summary = "保存或更新提示词文件模板", description = "持久化保存提示词文件到项目并在内存中实时生效")
    @PostMapping
    public Result<PromptFileService.PromptTemplateInfo> saveTemplate(@RequestBody PromptSaveRequest req) {
        try {
            PromptFileService.PromptTemplateInfo info = promptFileService.saveOrUpdateTemplate(req.getTemplateId(), req.getContent());
            return Result.success("保存成功", info);
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    @Operation(summary = "手动重新扫描并重载提示词模板", description = "从 classpath 及资源路径强制刷新内存中的模板缓存")
    @PostMapping("/reload")
    public Result<Boolean> reloadTemplates() {
        promptFileService.reloadTemplates();
        return Result.success("重载成功", true);
    }
}
