package com.ragagent.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDocumentChunkMapper;
import com.ragagent.rag.mapper.AiDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private final RagDocumentParser documentParser;
    private final RagChunker ragChunker;
    private final AiDatasetMapper datasetMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;
    @org.springframework.beans.factory.annotation.Value("${EMBEDDING_BATCH_SIZE:32}")
    private int embeddingBatchSize;

    /**
     * 异步解析并切片入库
     */
    @Async
    public void indexDocumentAsync(Long documentId) {
        AiDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("文档不存在: {}", documentId);
            return;
        }

        try {
            // 1. 更新状态为解析中
            doc.setStatus("PARSING");
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);

            // 获取知识库配置
            AiDataset dataset = datasetMapper.selectById(doc.getDatasetId());
            int chunkSize = dataset != null && dataset.getChunkSize() != null ? dataset.getChunkSize() : 800;
            int chunkOverlap = dataset != null && dataset.getChunkOverlap() != null ? dataset.getChunkOverlap() : 100;

            // 2. 文本提取
            File file = new File(doc.getFilePath());
            if (!file.exists()) {
                throw new RuntimeException("文件物理路径不存在: " + doc.getFilePath());
            }
            String rawText = documentParser.parseToString(file);

            // 3. 语义分块
            List<String> textChunks = ragChunker.splitText(rawText, chunkSize, chunkOverlap);
            log.info("文档 [{}] 提取文本完成，切片数量: {}", doc.getName(), textChunks.size());

            // 4. 清理旧切片数据 (如果重新处理)
            chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, doc.getId()));
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", String.valueOf(doc.getId()));
                log.info("已清理 vector_store 中文档 [{}] 的旧向量切片", doc.getId());
            } catch (Exception e) {
                log.warn("清理旧向量切片出现异常 (可能表尚未初始化): {}", e.getMessage());
            }

            // 5. 组装切片实体与批量持久化
            List<Document> springAiDocs = new ArrayList<>();
            int totalTokens = 0;

            for (int i = 0; i < textChunks.size(); i++) {
                String content = textChunks.get(i);
                int tokenEstimate = content.length() / 2;
                totalTokens += tokenEstimate;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("datasetId", doc.getDatasetId());
                metadata.put("documentId", doc.getId());
                metadata.put("docName", doc.getName());
                metadata.put("chunkIndex", i);

                AiDocumentChunk chunk = AiDocumentChunk.builder()
                        .datasetId(doc.getDatasetId())
                        .documentId(doc.getId())
                        .chunkIndex(i)
                        .content(content)
                        .tokenCount(tokenEstimate)
                        .metadata(metadata.toString())
                        .createTime(LocalDateTime.now())
                        .build();
                chunkMapper.insert(chunk);
                metadata.put("chunkId", chunk.getId());

                // 组装 Spring AI 向量化对象
                Document springDoc = new Document(content, metadata);
                springAiDocs.add(springDoc);
            }

            // 6. 调用向量库持久化 (若已配置 VectorStore 且启用 embedding 模型)
            if (embeddingEnabled) {
                VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
                if (vectorStore != null && !springAiDocs.isEmpty()) {
                    try {
                        log.info("开始将文档 [{}] 的 {} 个切片向量化写入向量库 (批处理大小: {})...",
                                doc.getName(), springAiDocs.size(), embeddingBatchSize);
                        for (int i = 0; i < springAiDocs.size(); i += embeddingBatchSize) {
                            int end = Math.min(i + embeddingBatchSize, springAiDocs.size());
                            List<Document> batch = springAiDocs.subList(i, end);
                            vectorStore.add(batch);
                            log.info("切片向量化批次进度: [{}/{}]", end, springAiDocs.size());
                        }
                        log.info("全部切片向量写入向量库成功!");
                    } catch (Exception e) {
                        log.warn("向量化写入异常(可能是远程模型网关未配置embedding)，已安全降级为关系库全文索引: {}", e.getMessage());
                    }
                }
            } else {
                log.info("当前环境未开启远程 Embedding 模型，文档切片已安全存储于关系数据库提供全文检索能力");
            }

            // 7. 更新文档及知识库精准统计信息
            doc.setStatus("COMPLETED");
            doc.setChunkCount(textChunks.size());
            doc.setTokenCount(totalTokens);
            doc.setErrorMsg("");
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);

            if (dataset != null) {
                Long actualDocCount = documentMapper.selectCount(new LambdaQueryWrapper<AiDocument>()
                        .eq(AiDocument::getDatasetId, dataset.getId()));
                List<AiDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                        .eq(AiDocument::getDatasetId, dataset.getId()));
                int actualChunkTotal = docs.stream()
                        .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                        .sum();
                dataset.setDocCount(actualDocCount.intValue());
                dataset.setChunkCount(actualChunkTotal);
                datasetMapper.updateById(dataset);
            }

        } catch (Exception e) {
            log.error("文档切片处理失败: {}", doc.getName(), e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }
}
