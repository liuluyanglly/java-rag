package com.ragagent.agent.tools;

import com.ragagent.rag.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RagSearchService ragSearchService;

    public record KnowledgeSearchRequest(String query) {}

    /**
     * 定义知识库检索工具
     */
    @Tool(description = "根据关键词在企业知识库中检索相关的技术文档、规章制度或业务知识")
    public String searchKnowledge(KnowledgeSearchRequest request) {
        log.info("Agent 触发知识库检索工具: query={}", request.query());
        List<RagSearchService.SearchResultChunk> chunks = ragSearchService.search(request.query(), null, 3);
        if (chunks.isEmpty()) {
            return "知识库中未检索到相关内容。";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagSearchService.SearchResultChunk chunk = chunks.get(i);
            sb.append("[").append(i + 1).append("] 来源: ").append(chunk.getDocumentName()).append("\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
