package com.ragagent.agent.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文件的提示词模板管理服务 (Spring AI 2.0 原生规范)
 * 支持从 classpath:/prompts/*.st 及本地配置目录扫描、加载、动态解析变量与高效渲染
 */
@Slf4j
@Service
public class PromptFileService {

    private final Map<String, PromptTemplateInfo> templateCache = new ConcurrentHashMap<>();
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptTemplateInfo {
        private String templateId;       // 模板唯一键，例如 system-default
        private String name;             // 模板中文名称
        private String description;      // 描述
        private String fileName;         // 物理文件名，例如 system-default.st
        private String rawContent;       // 原始内容
        private List<String> variables;  // 提取出的占位变量列表
    }

    @PostConstruct
    public void init() {
        reloadTemplates();
    }

    /**
     * 重新扫描并加载 classpath 下所有的提示词模板文件
     */
    public synchronized void reloadTemplates() {
        templateCache.clear();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:/prompts/*.st");
            for (Resource res : resources) {
                String filename = res.getFilename();
                if (!StringUtils.hasText(filename)) continue;

                String templateId = filename.replace(".st", "");
                String content = StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
                PromptTemplateInfo info = buildTemplateInfo(templateId, filename, content);
                templateCache.put(templateId, info);
                log.info("已加载提示词模板文件: id={}, 变量={}", templateId, info.getVariables());
            }
        } catch (Exception e) {
            log.error("扫描加载提示词模板失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有可用的提示词模板列表
     */
    public List<PromptTemplateInfo> listTemplates() {
        return new ArrayList<>(templateCache.values());
    }

    /**
     * 根据 templateId 查询单个模板
     */
    public Optional<PromptTemplateInfo> getTemplate(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(templateCache.get(templateId));
    }

    /**
     * 基于 Spring AI 2.0 PromptTemplate 进行动态参数渲染
     */
    public String render(String templateId, Map<String, Object> variables) {
        PromptTemplateInfo info = templateCache.get(templateId);
        if (info == null) {
            throw new IllegalArgumentException("未找到对应的提示词模板: " + templateId);
        }
        return renderContent(info.getRawContent(), variables);
    }

    /**
     * 渲染任意内容中的占位符
     */
    public String renderContent(String rawContent, Map<String, Object> variables) {
        if (!StringUtils.hasText(rawContent)) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return rawContent;
        }

        // 使用 Spring AI 原生 PromptTemplate 渲染
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template(rawContent)
                .build();
        return promptTemplate.render(variables);
    }

    /**
     * 保存或更新提示词模板文件
     */
    public synchronized PromptTemplateInfo saveOrUpdateTemplate(String templateId, String content) {
        if (!StringUtils.hasText(templateId) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("模板ID和模板内容不能为空");
        }
        String fileName = templateId + ".st";

        // 更新内存缓存
        PromptTemplateInfo info = buildTemplateInfo(templateId, fileName, content);
        templateCache.put(templateId, info);

        // 尝试持久化保存到 resources/prompts 目录 (开发阶段)
        try {
            File promptsDir = new File("backend/src/main/resources/prompts");
            if (promptsDir.exists() && promptsDir.isDirectory()) {
                File targetFile = new File(promptsDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                }
                log.info("提示词模板已写入磁盘文件: {}", targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("写入本地提示词文件警告: {}", e.getMessage());
        }

        return info;
    }

    private PromptTemplateInfo buildTemplateInfo(String templateId, String fileName, String content) {
        List<String> variables = extractVariables(content);
        String name = resolveName(templateId, content);
        String desc = resolveDescription(content);

        return PromptTemplateInfo.builder()
                .templateId(templateId)
                .name(name)
                .description(desc)
                .fileName(fileName)
                .rawContent(content)
                .variables(variables)
                .build();
    }

    private List<String> extractVariables(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VARIABLE_PATTERN.matcher(content);
        while (m.find()) {
            vars.add(m.group(1));
        }
        return new ArrayList<>(vars);
    }

    private String resolveName(String templateId, String content) {
        // 从第一行 "# 角色" 或第一句话提炼名称
        if (content.contains("客服")) return "企业智能客服顾问";
        if (content.contains("架构师") || content.contains("代码")) return "资深全栈架构师";
        if (content.contains("知识库") || content.contains("RAG")) return "企业知识库严谨问答";
        if ("system-default".equals(templateId)) return "企业通用协同助理";
        return templateId;
    }

    private String resolveDescription(String content) {
        // 截取前 60 个字符作为简介
        String clean = content.replaceAll("[#\\-*\\n\\r]", " ").trim();
        return clean.length() > 60 ? clean.substring(0, 60) + "..." : clean;
    }
}
