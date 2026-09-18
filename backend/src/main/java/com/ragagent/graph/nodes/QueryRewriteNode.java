package com.ragagent.graph.nodes;

import com.ragagent.graph.GraphNode;
import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Agentic RAG 自愈重构与查询拓展节点 (QueryRewriteNode)
 * 职责：当 CriticNode 判定证据不足或存在盲区时，分析未命中原因，
 *      进行意图多阶段拆解、同义词扩展与子问题重构，再回流至 RetrievalNode 进行第二轮深搜。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriteNode implements GraphNode {

    private final ObjectProvider<ChatModel> chatModelProvider;

    @Override
    public GraphState execute(GraphState state) {
        int currentRetry = (Integer) state.getData().getOrDefault("retryCount", 0);
        currentRetry++;
        state.put("retryCount", currentRetry);

        log.info("[QueryRewriteNode] 启动第 {} 轮查询意图重构与自愈拓搜: topic={}", currentRetry, state.getTopic());
        state.addThought("🔄 智能体自愈回路激活（第 " + currentRetry + " 次重构）：原始检索词覆盖度不足，启动意图分解与自适应深搜词生成...");

        String originalTopic = state.getTopic();
        String rewrittenQuery = originalTopic;

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            try {
                String prompt = String.format("""
                        你是一个专业的信息检索与 Agentic RAG 提问重构专家。
                        当前用户的研究课题是: 【%s】
                        上一轮直接检索未命中充足切片。请为本课题提炼出 2~3 个最核心、最关键、便于切片命中的核心检索关键词短语，用空格分隔，严禁输出任何多余废话或标点。
                        示例输出：Agentic RAG 状态机 架构选型 对比
                        """, originalTopic);

                String result = ChatClient.create(chatModel)
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (result != null && !result.isBlank()) {
                    rewrittenQuery = result.trim().replaceAll("[\\r\\n]+", " ");
                    log.info("[QueryRewriteNode] 大模型重写检索词成功: '{}'", rewrittenQuery);
                }
            } catch (Exception e) {
                log.warn("[QueryRewriteNode] 大模型重写词异常，采用启发式拓展: {}", e.getMessage());
                rewrittenQuery = originalTopic + " 架构规范 最佳实践 核心机制";
            }
        } else {
            rewrittenQuery = originalTopic + " 关键技术 实施路径 评估对比";
        }

        // 保存本次重构后的查询
        state.put("currentSearchQuery", rewrittenQuery);
        state.addThought("💡 提问重构完成：提炼聚焦检索关键词为【" + rewrittenQuery + "】，重新唤醒混合检索器 (RetrievalNode) 展开定向回溯。");

        return state;
    }
}
