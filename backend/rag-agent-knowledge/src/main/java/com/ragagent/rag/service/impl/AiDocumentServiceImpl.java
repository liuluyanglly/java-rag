package com.ragagent.rag.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDocumentChunkMapper;
import com.ragagent.rag.mapper.AiDocumentMapper;
import com.ragagent.rag.service.AiDocumentService;
import com.ragagent.rag.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl extends ServiceImpl<AiDocumentMapper, AiDocument> implements AiDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentServiceImpl.class);

    private final AiDocumentChunkMapper chunkMapper;
    private final AiDatasetMapper datasetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RagIndexingService indexingService;

    @Override
    public Page<AiDocument> pageDocuments(Long datasetId, Integer pageNum, Integer pageSize) {
        Page<AiDocument> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiDocument::getDatasetId, datasetId)
                .orderByDesc(AiDocument::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Page<AiDocumentChunk> pageChunks(Long documentId, Integer pageNum, Integer pageSize) {
        Page<AiDocumentChunk> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiDocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiDocumentChunk::getDocumentId, documentId)
                .orderByAsc(AiDocumentChunk::getChunkIndex);
        return chunkMapper.selectPage(page, wrapper);
    }

    @Override
    public Mono<AiDocument> uploadAndIndexDocument(Long datasetId, Mono<FilePart> filePartMono, Long userId) {
        return filePartMono.flatMap(filePart -> {
            String originalFilename = filePart.filename();
            String extension = FileUtil.extName(originalFilename);
            String saveName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

            // 本地存储目录：uploads/datasets/{datasetId}/
            String uploadDir = "uploads/datasets/" + datasetId + "/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File destFile = new File(uploadDir + saveName);

            return filePart.transferTo(destFile).then(Mono.fromCallable(() -> {
                AiDocument document = AiDocument.builder()
                        .datasetId(datasetId)
                        .name(originalFilename)
                        .filePath(destFile.getAbsolutePath())
                        .fileSize(destFile.length())
                        .fileType(extension.toLowerCase())
                        .status("PARSING") // 解析中
                        .chunkCount(0)
                        .tokenCount(0)
                        .createdBy(userId)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                save(document);

                // 异步触发切片与向量化入库
                indexingService.indexDocumentAsync(document.getId());
                return document;
            }));
        });
    }

    @Override
    public void retryIndexing(Long documentId) {
        AiDocument document = getById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        document.setStatus("PARSING");
        document.setErrorMsg(null);
        document.setUpdateTime(LocalDateTime.now());
        updateById(document);
        indexingService.indexDocumentAsync(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentAndChunks(Long documentId) {
        AiDocument document = getById(documentId);
        if (document != null) {
            // 删除物理文件
            if (document.getFilePath() != null) {
                FileUtil.del(document.getFilePath());
            }
            // 删除切片记录
            chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, documentId));

            // 同步清理 pgvector 向量存储中的文档切片
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'document_id' = ?", String.valueOf(documentId));
            } catch (Exception e) {
                log.warn("清理 vector_store 失败或表不存在: {}", e.getMessage());
            }

 // 更新知识库切片总数字段
 AiDataset dataset = datasetMapper.selectById(document.getDatasetId());
 if (dataset != null && dataset.getChunkCount() != null && document.getChunkCount() != null) {
 dataset.setChunkCount(Math.max(0, dataset.getChunkCount() - document.getChunkCount()));
 dataset.setUpdateTime(LocalDateTime.now());
 datasetMapper.updateById(dataset);
 }

 // 逻辑删除主文档
 removeById(documentId);
 }
 }
}
