package com.ragagent.rag.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface AiDocumentService extends IService<AiDocument> {

    Page<AiDocument> pageDocuments(Long datasetId, Integer pageNum, Integer pageSize);

    Page<AiDocumentChunk> pageChunks(Long documentId, Integer pageNum, Integer pageSize);

    Mono<AiDocument> uploadAndIndexDocument(Long datasetId, Mono<FilePart> filePartMono, Long userId);

    void retryIndexing(Long documentId);

    void deleteDocumentAndChunks(Long documentId);
}
