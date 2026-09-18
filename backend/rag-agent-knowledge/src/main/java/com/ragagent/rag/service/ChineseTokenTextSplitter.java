package com.ragagent.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI 2.0 中文增强文档分块器 (ChineseTokenTextSplitter)
 * 继承 Spring AI 原生 TextSplitter，支持纳入 Spring AI 标准 Document ETL 处理流
 * 重点补足原生 TokenTextSplitter 无法识别中文标点（。？！；）及段落断句的痛点
 */
@Slf4j
@Component
public class ChineseTokenTextSplitter extends TextSplitter {

    private final RagChunker ragChunker;
    private final int defaultChunkSize;
    private final int defaultOverlap;

    public ChineseTokenTextSplitter() {
        this(new RagChunker(), 800, 100);
    }

    public ChineseTokenTextSplitter(RagChunker ragChunker, int chunkSize, int overlap) {
        this.ragChunker = ragChunker;
        this.defaultChunkSize = chunkSize;
        this.defaultOverlap = overlap;
    }

    public ChineseTokenTextSplitter(int chunkSize) {
        this(new RagChunker(), chunkSize, Math.max(50, chunkSize / 8));
    }

    @Override
    protected List<String> splitText(String text) {
        return ragChunker.splitText(text, defaultChunkSize, defaultOverlap);
    }
}
