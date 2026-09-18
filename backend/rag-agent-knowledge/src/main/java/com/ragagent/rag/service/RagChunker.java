package com.ragagent.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RagChunker {

    private static final String PARAGRAPH_SPLIT_REGEX = "(\\r?\\n\\s*){2,}"; // 双换行/空行分割自然段落
    private static final String LINE_SPLIT_REGEX = "\\r?\\n"; // 单换行分割
    private static final String SENTENCE_SPLIT_REGEX = "(?<=[。！？!?；;])"; // 常见中英文句末标点

    /**
     * 段落优先智能分块算法：
     * 1. 优先按自然段落（换行/空行）进行切分，保证段落语义的纯粹性；
     * 2. 段落较短时自顶向下聚合成大小适中的切片 (不超过 chunkSize)，避免切出巨量细碎小块；
     * 3. 当单个段落太长 (> chunkSize) 时，降级按单行、句号标点或长度进行多级细分。
     *
     * @param text 完整文档文本
     * @param chunkSize 每个切片的目标长度 (字符数)
     * @param chunkOverlap 相邻切片重叠长度
     * @return 分块后的文本列表
     */
    public List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        if (chunkSize <= 0) {
            chunkSize = 800;
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            chunkOverlap = Math.min(100, chunkSize / 4);
        }

        // 1. 统一归一化换行符并去除首尾空白
        String cleanText = text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n").trim();
        if (cleanText.length() <= chunkSize) {
            chunks.add(cleanText);
            return chunks;
        }

        // 2. 第一级：按自然段落(空行)拆分为候选段落
        String[] rawParagraphs = cleanText.split(PARAGRAPH_SPLIT_REGEX);
        List<String> normalizedParagraphs = new ArrayList<>();

        for (String para : rawParagraphs) {
            String trimmed = para.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }

            // 若单个段落自身超长，进行二级拆解（按行、按句、按长度）
            if (trimmed.length() > chunkSize) {
                normalizedParagraphs.addAll(splitLongParagraph(trimmed, chunkSize, chunkOverlap));
            } else {
                normalizedParagraphs.add(trimmed);
            }
        }

        // 3. 自顶向下聚合段落：短段落连续合并至目标 chunkSize，杜绝过碎切片
        StringBuilder currentBuffer = new StringBuilder();

        for (String para : normalizedParagraphs) {
            if (currentBuffer.length() == 0) {
                currentBuffer.append(para);
            } else {
                // 如果当前缓存加上新段落不超过 chunkSize，合并为一个切片
                if (currentBuffer.length() + 2 + para.length() <= chunkSize) {
                    currentBuffer.append("\n\n").append(para);
                } else {
                    // 当前缓存已饱和，推入切片列表并开启新切片
                    chunks.add(currentBuffer.toString().trim());
                    currentBuffer.setLength(0);
                    currentBuffer.append(para);
                }
            }
        }

        // 补录最后留存的段落切片
        if (currentBuffer.length() > 0) {
            chunks.add(currentBuffer.toString().trim());
        }

        log.info("🚀 [RagChunker] 段落优先切片完成: 原始字符数={}, 生成切片数={}, 目标大小={}, 重叠={}",
                cleanText.length(), chunks.size(), chunkSize, chunkOverlap);

        return chunks;
    }

    /**
     * 超长段落的多级细分策略：
     * 1. 优先按单换行细分；
     * 2. 若单行依然超长，按中英文句号、感叹号、问号、分号断句细分；
     * 3. 若单个句子依然超长 (如无标点超长表格/长字符)，以带 overlap 的滑动窗口切分。
     */
    private List<String> splitLongParagraph(String paragraph, int chunkSize, int chunkOverlap) {
        List<String> result = new ArrayList<>();

        // 尝试按单行切分
        String[] lines = paragraph.split(LINE_SPLIT_REGEX);
        if (lines.length > 1) {
            StringBuilder lineBuf = new StringBuilder();
            for (String line : lines) {
                String lineTrim = line.trim();
                if (!StringUtils.hasText(lineTrim)) {
                    continue;
                }
                if (lineTrim.length() > chunkSize) {
                    if (lineBuf.length() > 0) {
                        result.add(lineBuf.toString().trim());
                        lineBuf.setLength(0);
                    }
                    result.addAll(splitBySentencesOrSlidingWindow(lineTrim, chunkSize, chunkOverlap));
                } else {
                    if (lineBuf.length() + 1 + lineTrim.length() <= chunkSize) {
                        if (lineBuf.length() > 0) lineBuf.append("\n");
                        lineBuf.append(lineTrim);
                    } else {
                        result.add(lineBuf.toString().trim());
                        lineBuf.setLength(0);
                        lineBuf.append(lineTrim);
                    }
                }
            }
            if (lineBuf.length() > 0) {
                result.add(lineBuf.toString().trim());
            }
            return result;
        }

        // 单行本身就超长的情况
        return splitBySentencesOrSlidingWindow(paragraph, chunkSize, chunkOverlap);
    }

    /**
     * 按句子标点细分；如果单句依然超长，进入滑动窗口兜底
     */
    private List<String> splitBySentencesOrSlidingWindow(String text, int chunkSize, int chunkOverlap) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split(SENTENCE_SPLIT_REGEX);

        if (sentences.length > 1) {
            StringBuilder sentBuf = new StringBuilder();
            for (String sent : sentences) {
                String sentTrim = sent.trim();
                if (!StringUtils.hasText(sentTrim)) {
                    continue;
                }
                if (sentTrim.length() > chunkSize) {
                    if (sentBuf.length() > 0) {
                        result.add(sentBuf.toString().trim());
                        sentBuf.setLength(0);
                    }
                    result.addAll(slidingWindowSplit(sentTrim, chunkSize, chunkOverlap));
                } else {
                    if (sentBuf.length() + sentTrim.length() <= chunkSize) {
                        sentBuf.append(sentTrim);
                    } else {
                        result.add(sentBuf.toString().trim());
                        sentBuf.setLength(0);
                        sentBuf.append(sentTrim);
                    }
                }
            }
            if (sentBuf.length() > 0) {
                result.add(sentBuf.toString().trim());
            }
            return result;
        }

        // 纯滑动窗口切分
        return slidingWindowSplit(text, chunkSize, chunkOverlap);
    }

    /**
     * 滑动窗口兜底切分 (针对单行巨型无标点文本)
     */
    private List<String> slidingWindowSplit(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - chunkOverlap;
        if (step <= 0) {
            step = chunkSize / 2;
        }
        int length = text.length();
        for (int i = 0; i < length; i += step) {
            int end = Math.min(i + chunkSize, length);
            String sub = text.substring(i, end).trim();
            if (StringUtils.hasText(sub)) {
                chunks.add(sub);
            }
            if (end >= length) {
                break;
            }
        }
        return chunks;
    }
}

