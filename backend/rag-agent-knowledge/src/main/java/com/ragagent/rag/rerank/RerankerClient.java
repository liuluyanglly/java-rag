package com.ragagent.rag.rerank;

import java.util.List;

/**
 * <h3>标准化重排序客户端接口 (Reranker Client Interface)</h3>
 * <p>
 * 定义重排序的标准规范，支持无缝切换与扩展：
 * <ul>
 *   <li>{@link BgeHttpRerankerClient}: 工业级深度学习重排模型 (对接 BGE-Reranker-Large / TEI / Xinference / Cohere)</li>
 *   <li>{@link RuleBasedRerankerClient}: 本地轻量化词频覆盖加权规则 (高可用兜底)</li>
 * </ul>
 *
 * @author Java-RAG Team
 */
public interface RerankerClient {

    /**
     * 对候选切片集合基于问题相关性执行精排打分并排序
     *
     * @param query     用户原始或改写后的提问
     * @param documents 待打分的候选切片文本列表
     * @param topN      期望截取的高分切片数量，若为 null 或 <= 0 则全部返回
     * @return 经过重排序并按置信度降序排列的结果集合
     */
    List<RerankResult> rerank(String query, List<String> documents, Integer topN);

    /**
     * 当前客户端的名称或类型标识 (如 "BGE_HTTP", "RULE_BASED")
     */
    String getType();
}
