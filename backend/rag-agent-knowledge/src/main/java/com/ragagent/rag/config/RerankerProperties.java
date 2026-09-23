package com.ragagent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h3>Reranker 模型重排序配置属性 (Reranker Properties)</h3>
 *
 * @author Java-RAG Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.reranker")
public class RerankerProperties {

    /** 是否启用远程 Reranker 深度重排 (若为 false 则直接使用本地规则打分) */
    private boolean enabled = true;

    /** 重排序实现类型: BGE_HTTP (默认), RULE_BASED (纯本地规则) */
    private String type = "BGE_HTTP";

    /** 远程 Reranker API 基础地址 (如 Xinference, TEI, vLLM 或独立 BGE 部署端点) */
    private String baseUrl = "http://192.168.100.90:8012";

    /** 鉴权密钥 (若服务免鉴权可留空) */
    private String apiKey = "empty";

    /** 重排序模型名称 (如 BAAI/bge-reranker-large, BAAI/bge-reranker-v2-m3) */
    private String model = "BAAI/bge-reranker-large";

    /** HTTP 请求超时时间 (毫秒，默认 5 秒) */
    private int timeoutMs = 5000;

    /** 最低相关性置信阈值 (低于该得分的切片在精排后将被自动丢弃以减少幻觉，默认 0.20) */
    private double minScore = 0.20;
}
