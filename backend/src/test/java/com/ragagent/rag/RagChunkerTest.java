package com.ragagent.rag;

import com.ragagent.rag.service.RagChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RagChunkerTest {

    private final RagChunker chunker = new RagChunker();

    @Test
    public void testShortParagraphMerge() {
        String doc = "# 标题1\n\n这是第一段，介绍系统架构。\n\n这是第二段，介绍数据存储。\n\n这是第三段，介绍检索机制。";
        List<String> chunks = chunker.splitText(doc, 500, 50);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("这是第一段"));
        assertTrue(chunks.get(0).contains("这是第三段"));
    }

    @Test
    public void testLongParagraphSplit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("这是句号结尾的完整长句子编号").append(i).append("。");
        }
        String doc = sb.toString();

        List<String> chunks = chunker.splitText(doc, 100, 20);
        assertTrue(chunks.size() > 1);
    }
}