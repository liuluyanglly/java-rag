package com.ragagent.rag.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <h3>重排序结果模型 (Rerank Result)</h3>
 *
 * @author Java-RAG Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /** 原始候选切片在输入列表中的索引下标 (0-indexed) */
    private int index;

    /** 重排序模型计算得出的相关度置信得分 (通常在 0.0 ~ 1.0 之间) */
    private double score;

    /** 切片核心文本内容 */
    private String text;
}
