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

/**
 * <h1>RAG 知识库索引与文档切片构建服务 (RAG Indexing Service)</h1>
 * <p>
 * 本服务是企业级知识库管理的核心引擎，负责将非结构化文档转化为高维向量并构建双轨索引。
 * 全生命周期包含以下 7 大核心阶段：
 * <ol>
 *   <li><b>异步任务派发 (Async Processing)</b>：利用 Spring 的 {@code @Async} 结合虚拟线程池执行耗时的文档提取与 Embedding 调用，避免阻塞 HTTP 响应。</li>
 *   <li><b>多格式文档提取 (Document Parsing)</b>：自动识别 PDF、Word、Markdown、纯文本，调用 Apache Tika 或 MinerU 智能提取正文。</li>
 *   <li><b>语义分块与滑动重叠 (Semantic Chunking & Overlap)</b>：采用中文感知的 Token 切片器，在段落/句子边界切片，并保留 Overlap 重叠窗口以防关键语义跨块截断。</li>
 *   <li><b>幂等性旧数据清理 (Idempotent Purge)</b>：文档重新解析时，原子性清理旧的切片记录及向量表中关联的向量数据。</li>
 *   <li><b>元数据组装与关系库持久化</b>：为每个切片注入 datasetId、documentId、chunkIndex 等多维上下文，便于精细化权限过滤。</li>
 *   <li><b>分批向量化写入 (Batch Embedding & VectorStore)</b>：按批次（如 32 条）调用远端 Embedding 模型写入 pgvector，防止触发网关请求体过大限流。</li>
 *   <li><b>容错自愈与降级机制</b>：当向量模型网关不可用或未配置时，安全降级保留关系库全文索引，保证系统高可用性。</li>
 * </ol>
 *
 * @author Java-RAG Team
 * @see RagDocumentParser
 * @see RagChunker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexingService {

    /** 多格式文档文本解析器（支持 PDF/DOCX/MD/TXT） */
    private final RagDocumentParser documentParser;
    /** 文本语义切片器（支持动态 chunkSize 与 overlap 窗口） */
    private final RagChunker ragChunker;
    /** 知识库元数据持久层 Mapper */
    private final AiDatasetMapper datasetMapper;
    /** 知识库文档持久层 Mapper */
    private final AiDocumentMapper documentMapper;
    /** 知识库切片实体持久层 Mapper */
    private final AiDocumentChunkMapper chunkMapper;
    /** Spring JDBC 模板，用于直接执行高效的向量表清理与特定 SQL 操作 */
    private final JdbcTemplate jdbcTemplate;
    /** Spring AI 向量存储提供者 (基于 ObjectProvider 实现优雅按需装配与可选依赖) */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /** 是否开启远程 Embedding 模型向量化（若为 false 则仅持久化文本并提供关键词检索） */
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.enabled:true}")
    private boolean embeddingEnabled;

    /** 向量化请求分批大小（默认每批 32 个切片，平衡网络 IO 吞吐与 API 限制） */
    @org.springframework.beans.factory.annotation.Value("${EMBEDDING_BATCH_SIZE:32}")
    private int embeddingBatchSize;

    /**
     * <h3>异步执行文档解析、语义切片与向量入库全流水线</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li><b>为什么采用 @Async 异步执行？</b> 大文档（几十页 PDF）解析可能耗时数秒至数十秒，同步等待会导致网关超时且极大占用 Web 容器请求线程。</li>
     *   <li><b>为什么需要 Overlap（重叠滑动窗口）？</b> 若把一句话拆分在两个切片两端，向量检索时单独某一半切片的语义均不完整。保留 100 字符的重叠窗口可保证上下文连续性。</li>
     *   <li><b>为什么采用分批次写入（Batching）？</b> 远程大模型提供商（如 OpenAI、阿里云 DashScope）通常限制单次请求的 Token 上限（如 8192），按批次拆分能有效避免超限异常。</li>
     * </ul>
     *
     * @param documentId 待解析处理的文档主键 ID
     */
    @Async
    public void indexDocumentAsync(Long documentId) {
        // 步骤 1：校验文档元数据是否存在
        AiDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("【RagIndexing】待索引文档不存在，终止任务: documentId={}", documentId);
            return;
        }

        try {
            // 步骤 2：更新状态机流转为 PARSING（解析中），给前端即时提供进度反馈
            doc.setStatus("PARSING");
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);

            // 获取知识库自定义的切片配置（分块大小与重叠窗口）
            AiDataset dataset = datasetMapper.selectById(doc.getDatasetId());
            int chunkSize = dataset != null && dataset.getChunkSize() != null ? dataset.getChunkSize() : 800;
            int chunkOverlap = dataset != null && dataset.getChunkOverlap() != null ? dataset.getChunkOverlap() : 100;

            // 步骤 3：文本提取（多格式解析与正文清洗）
            File file = new File(doc.getFilePath());
            if (!file.exists()) {
                throw new RuntimeException("文档物理文件未找到，路径: " + doc.getFilePath());
            }
            String rawText = documentParser.parseToString(file);

            // 步骤 4：基于中文语义边界进行分块处理 (切片)
            List<String> textChunks = ragChunker.splitText(rawText, chunkSize, chunkOverlap);
            log.info("【RagIndexing】文档 [{}] 提取文本完成，切片数量: {}", doc.getName(), textChunks.size());

            // 步骤 5：幂等性清理旧切片（支持用户反复重新解析同一文档）
            chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, doc.getId()));
            try {
                // 通过 PostgreSQL JSON 字段操作符 (->>) 批量删除旧向量记录
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", String.valueOf(doc.getId()));
                log.info("【RagIndexing】已清理 vector_store 中关联文档 [{}] 的旧向量切片", doc.getId());
            } catch (Exception e) {
                log.warn("【RagIndexing】清理旧向量切片出现异常 (可能向量表尚未自动初始化): {}", e.getMessage());
            }

            // 步骤 6：切片实体构建、关系库持久化与 Spring AI Document 对象映射
            List<Document> springAiDocs = new ArrayList<>();
            int totalTokens = 0;

            for (int i = 0; i < textChunks.size(); i++) {
                String content = textChunks.get(i);
                // 估算 Token 消耗（中文平均每个汉字约占 1~2 Token，取长度/2 作为粗略预估）
                int tokenEstimate = content.length() / 2;
                totalTokens += tokenEstimate;

                // 注入多维元数据，用于后续向量过滤检索（Filter Expression）
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("datasetId", doc.getDatasetId());
                metadata.put("documentId", doc.getId());
                metadata.put("docName", doc.getName());
                metadata.put("chunkIndex", i);

                // 保存到关系型数据库（用于全文检索、切片阅读与溯源高亮）
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

                // 组装 Spring AI 专用的 Document 对象（携带文本正文与元数据字典）
                Document springDoc = new Document(content, metadata);
                springAiDocs.add(springDoc);
            }

            // 步骤 7：分批调用 Embedding 模型并写入向量数据库 (VectorStore)
            if (embeddingEnabled) {
                VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
                if (vectorStore != null && !springAiDocs.isEmpty()) {
                    try {
                        log.info("【RagIndexing】开始分批将文档 [{}] 的 {} 个切片向量化入库 (批大小: {})...",
                                doc.getName(), springAiDocs.size(), embeddingBatchSize);
                        for (int i = 0; i < springAiDocs.size(); i += embeddingBatchSize) {
                            int end = Math.min(i + embeddingBatchSize, springAiDocs.size());
                            List<Document> batch = springAiDocs.subList(i, end);
                            // 调用远程嵌入模型计算向量并插入 pgvector 向量索引表中
                            vectorStore.add(batch);
                            log.info("【RagIndexing】切片向量化批次进度: [{}/{}]", end, springAiDocs.size());
                        }
                        log.info("【RagIndexing】全部切片向量化并写入向量库成功!");
                    } catch (Exception e) {
                        // 降级策略：即便远端向量模型失败，不抛出硬性崩溃，允许回退至关系库倒排全文检索
                        log.warn("【RagIndexing】向量化写入异常(可能是远程模型网关未配置或配额耗尽)，已安全降级为关系库全文检索: {}", e.getMessage());
                    }
                }
            } else {
                log.info("【RagIndexing】当前环境未开启远程 Embedding 模型，文档切片已安全存储于关系数据库提供全文检索能力");
            }

            // 步骤 8：状态流转为 COMPLETED，回写文档统计信息（切片数、总Token、成功状态）
            doc.setStatus("COMPLETED");
            doc.setChunkCount(textChunks.size());
            doc.setTokenCount(totalTokens);
            doc.setErrorMsg("");
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);

            // 步骤 9：更新所属知识库的宏观统计（文档总数、切片总数）
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
            // 异常兜底：若任何一步发生未捕获异常，记录错误信息并将状态更新为 FAILED，保证用户在页面看到明确原因
            log.error("【RagIndexing】文档切片全流程处理异常: docName={}", doc.getName(), e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }
}
