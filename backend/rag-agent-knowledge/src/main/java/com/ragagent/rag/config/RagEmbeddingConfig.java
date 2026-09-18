package com.ragagent.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Configuration
public class RagEmbeddingConfig {

    @Value("${spring.ai.openai.embedding.base-url:${EMBEDDING_BASE_URL:http://192.168.100.90:8012}}")
    private String baseUrl;

    @Value("${spring.ai.openai.embedding.api-key:${EMBEDDING_API_KEY:empty}}")
    private String apiKey;

    @Value("${spring.ai.openai.embedding.options.model:${EMBEDDING_MODEL_NAME:BAAI/bge-m3}}")
    private String modelName;

    @Value("${spring.ai.openai.embedding.options.dimensions:${EMBEDDING_EMBEDDING_DIM:1024}}")
    private Integer dimensions;

    @Value("${EMBEDDING_TIMEOUT_SEC:120}")
    private int timeoutSec;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.openai.embedding.enabled", havingValue = "true", matchIfMissing = true)
    public EmbeddingModel customEmbeddingModel() {
        // 自动归一化 URL，彻底解决末尾包含 /v1 导致拼接成 /v1/v1/embeddings 的 404 问题
        String cleanUrl = baseUrl.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        String embeddingsPath = "/v1/embeddings";
        if (cleanUrl.endsWith("/v1")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 3);
        }

        log.info("🚀 [Embedding-Engine] 注册高精度嵌入模型: cleanBaseUrl={}, embeddingsPath={}, model={}, dim={}, timeout={}s",
                cleanUrl, embeddingsPath, modelName, dimensions, timeoutSec);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(timeoutSec, 60)));

        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(factory);

        OpenAiApi openAiApi = new OpenAiApi(
                cleanUrl,
                new SimpleApiKey(apiKey),
                HttpHeaders.EMPTY,
                "/v1/chat/completions",
                embeddingsPath,
                restClientBuilder,
                WebClient.builder(),
                new DefaultResponseErrorHandler()
        );

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelName)
                .dimensions(dimensions)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
