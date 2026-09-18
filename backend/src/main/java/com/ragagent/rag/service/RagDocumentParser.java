package com.ragagent.rag.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ragagent.rag.config.MinerUProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能文档解析提取器
 * 优先调用 MinerU 高精度多模态解析服务 (支持复杂排版、LaTeX公式与表格识别)
 * 在异常或超时情况下自动无缝降级为本地 Apache Tika 解析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagDocumentParser {

    private final MinerUProperties minerUProperties;
    private final Tika tika = new Tika();

    /**
     * 解析任意格式的文档并提取结构化文本
     * @param file 本地待解析文件
     * @return 提取出的结构化 Markdown 或纯文本内容
     */
    public String parseToString(File file) {
        // 1. 优先尝试 MinerU 多模态高精度提取
        try {
            String mineruContent = parseWithMinerU(file);
            if (mineruContent != null && !mineruContent.trim().isEmpty()) {
                String cleaned = formatAndCleanContent(mineruContent.trim());
                log.info("MinerU 多模态高精度解析并排版清洗成功: file={}, 提取文本长度={}", file.getName(), cleaned.length());
                return cleaned;
            }
        } catch (Exception e) {
            log.warn("MinerU 服务解析异常，自动无缝降级至本地 Tika: file={}, 原因={}", file.getName(), e.getMessage());
        }

        // 2. 降级容错: 使用本地 Apache Tika 提取
        return parseWithTika(file);
    }

    /**
     * 调用内网 5090 MinerU 服务进行多模态深度解析
     */
    private String parseWithMinerU(File file) {
        String url = minerUProperties.getBaseUrl();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        url = url + "file_parse";

        log.info("向 MinerU 发起解析请求: url={}, file={}", url, file.getName());

        HttpResponse response = HttpRequest.post(url)
                .form("files", file)
                .form("lang_list", minerUProperties.getLang())
                .form("output_dir", minerUProperties.getOutputDir())
                .form("formula_enable", String.valueOf(minerUProperties.getEnableFormula()))
                .form("table_enable", String.valueOf(minerUProperties.getEnableTable()))
                .form("backend", minerUProperties.getBackend())
                .form("return_images", String.valueOf(minerUProperties.getReturnImages()))
                .timeout(minerUProperties.getTimeout())
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("MinerU 响应异常状态码: " + response.getStatus() + ", body: " + response.body());
        }

        String body = response.body();
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        // 解析 MinerU 返回体 (支持标准 JSON 结果或纯文本 Markdown)
        if (JSONUtil.isTypeJSON(body)) {
            try {
                JSONObject json = JSONUtil.parseObj(body);
                // 探测常见的 MinerU 返回字段: markdown, content, result, data
                if (json.containsKey("markdown")) {
                    return json.getStr("markdown");
                }
                if (json.containsKey("content")) {
                    return json.getStr("content");
                }
                if (json.containsKey("data")) {
                    String extracted = extractMarkdownContent(json.get("data"));
                    if (extracted != null && !extracted.trim().isEmpty()) {
                        return extracted;
                    }
                }
                if (json.containsKey("results")) {
                    String extracted = extractMarkdownContent(json.get("results"));
                    if (extracted != null && !extracted.trim().isEmpty()) {
                        return extracted;
                    }
                }
            } catch (Exception e) {
                log.warn("解析 MinerU JSON 结构出现非致命异常: {}", e.getMessage());
            }
        }

        return body;
    }

    /**
     * 从 MinerU 嵌套数据结构中递归提取纯 Markdown 正文 (md_content)，彻底剥离 images Base64
     */
    private String extractMarkdownContent(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String str) {
            return str;
        }
        if (data instanceof JSONObject obj) {
            if (obj.containsKey("md_content")) {
                return obj.getStr("md_content");
            }
            if (obj.containsKey("markdown")) {
                return obj.getStr("markdown");
            }
            if (obj.containsKey("content")) {
                return obj.getStr("content");
            }

            StringBuilder sb = new StringBuilder();
            for (String key : obj.keySet()) {
                Object child = obj.get(key);
                if (child instanceof JSONObject childObj) {
                    if (childObj.containsKey("md_content")) {
                        sb.append(childObj.getStr("md_content")).append("\n\n");
                    } else if (childObj.containsKey("markdown")) {
                        sb.append(childObj.getStr("markdown")).append("\n\n");
                    } else if (childObj.containsKey("content")) {
                        sb.append(childObj.getStr("content")).append("\n\n");
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }
        return null;
    }

    /**
     * 本地 Apache Tika 兼容兜底解析
     */
    private String parseWithTika(File file) {
        log.info("执行本地 Apache Tika 兜底解析: {}", file.getName());
        try (InputStream stream = new FileInputStream(file)) {
            String content = tika.parseToString(stream);
            if (content == null) {
                return "";
            }
            return content.replaceAll("\\r\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
        } catch (Exception e) {
            log.error("Tika 文档兜底解析失败: {}", file.getName(), e);
            throw new RuntimeException("文档解析彻底失败: " + e.getMessage());
        }
    }

    /**
     * 文本格式排版与美化清洗器：
     * 1. 自动将 MinerU 生成的 <table> 标签转换为标准 Markdown 表格；
     * 2. 清理 <br> 等无用 HTML 标签为纯净换行；
     * 3. 归一化空白与连续空行。
     */
    public String formatAndCleanContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 1. 处理常见换行和实体符号
        String text = content.replaceAll("<br\\s*/?>", "\n")
                .replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&nbsp;", " ");

        // 2. 匹配并转换 HTML 表格 <table>...</table>
        java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile("(?is)<table[^>]*>(.*?)</table>");
        java.util.regex.Matcher tableMatcher = tablePattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (tableMatcher.find()) {
            String tableHtml = tableMatcher.group(1);
            String markdownTable = convertSingleHtmlTable(tableHtml);
            tableMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("\n\n" + markdownTable + "\n\n"));
        }
        tableMatcher.appendTail(sb);
        text = sb.toString();

        // 3. 清除残留的空标签与多余空行
        return text.replaceAll("(?i)<[/]?(p|div|span|tbody|thead|tfoot)[^>]*>", "")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String convertSingleHtmlTable(String tableInnerHtml) {
        java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
        java.util.regex.Matcher rowMatcher = rowPattern.matcher(tableInnerHtml);
        java.util.regex.Pattern cellPattern = java.util.regex.Pattern.compile("(?is)<(td|th)[^>]*>(.*?)</\\1>");

        List<List<String>> rows = new ArrayList<>();
        int maxCols = 0;

        while (rowMatcher.find()) {
            String rowContent = rowMatcher.group(1);
            java.util.regex.Matcher cellMatcher = cellPattern.matcher(rowContent);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                String cellText = cellMatcher.group(2)
                        .replaceAll("<[^>]+>", "") // 剔除内部可能存在的内联标签
                        .replaceAll("\\r?\\n", " ") // 单元格内换行转空格
                        .trim();
                cells.add(cellText.isEmpty() ? "-" : cellText);
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                maxCols = Math.max(maxCols, cells.size());
            }
        }

        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder md = new StringBuilder();
        // 第一行作为表头
        List<String> header = rows.get(0);
        md.append("|");
        for (int c = 0; c < maxCols; c++) {
            String val = c < header.size() ? header.get(c) : "-";
            md.append(" ").append(val).append(" |");
        }
        md.append("\n|");
        for (int c = 0; c < maxCols; c++) {
            md.append(" :--- |");
        }
        md.append("\n");

        // 剩余各行作为数据行
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            md.append("|");
            for (int c = 0; c < maxCols; c++) {
                String val = c < row.size() ? row.get(c) : "-";
                md.append(" ").append(val).append(" |");
            }
            md.append("\n");
        }

        return md.toString().trim();
    }
}
