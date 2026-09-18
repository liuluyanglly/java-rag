package com.ragagent.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.common.result.Result;
import com.ragagent.common.security.SecurityUtils;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDocumentChunkMapper;
import com.ragagent.rag.mapper.AiDocumentMapper;
import com.ragagent.rag.service.RagIndexingService;
import com.ragagent.rag.service.AiDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "知识库文档管理")
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final AiDocumentService documentService;

    @Operation(summary = "按知识库查询文档列表",
            description = "按上传时间倒序返回指定知识库下的文档，可据 status 字段观察切片解析进度")
    @SaCheckPermission("ai:dataset:list")
    @GetMapping("/list")
    public Result<Page<AiDocument>> list(
            @Parameter(description = "知识库ID", example = "1", required = true)
            @RequestParam Long datasetId,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            ServerWebExchange exchange) {
        return Result.success(documentService.pageDocuments(datasetId, pageNum, pageSize));
    }

    @Operation(summary = "上传文档并触发异步向量化切片",
            description = "支持 PDF、DOCX、Markdown、TXT 格式文件。上传成功后返回初始文档记录（status=0 解析中），后台异步提取文本并写入 pgvector")
    @SaCheckPermission("ai:dataset:edit")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<AiDocument>> upload(
            @Parameter(description = "知识库ID", example = "1", required = true)
            @RequestParam("datasetId") Long datasetId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "待解析文档（支持 pdf / docx / md / txt）",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "object"),
                            schemaProperties = {
                                    @SchemaProperty(name = "file", schema = @Schema(type = "string", format = "binary", description = "上传文件"))
                            }
                    )
            )
            @RequestPart("file") Mono<FilePart> filePartMono,
            ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserId(exchange);
        return documentService.uploadAndIndexDocument(datasetId, filePartMono, userId)
                .map(doc -> Result.success("文档上传成功，后台已启动异步切片与向量化", doc));
    }

    @Operation(summary = "查询文档的切片列表",
            description = "按 chunk_index 升序返回指定文档的所有分块，含切片文本与字符数")
    @SaCheckPermission("ai:dataset:list")
    @GetMapping("/{documentId}/chunks")
    public Result<Page<AiDocumentChunk>> chunks(
            @Parameter(description = "文档ID", example = "1", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            ServerWebExchange exchange) {
        return Result.success(documentService.pageChunks(documentId, pageNum, pageSize));
    }

    @Operation(summary = "重试文档切片与向量化",
            description = "用于解析失败（status=2）文档的重新触发")
    @SaCheckPermission("ai:dataset:edit")
    @PostMapping("/{id}/retry")
    public Result<Void> retry(
            @Parameter(description = "文档ID", example = "1", required = true)
            @PathVariable Long id,
            ServerWebExchange exchange) {
        try {
            documentService.retryIndexing(id);
            return Result.success("已重新触发切片", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "删除文档",
            description = "级联清理物理文件、文档切片表及 pgvector 向量存储中的关联向量数据")
    @SaCheckPermission("ai:dataset:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "文档ID", example = "1", required = true)
            @PathVariable Long id,
            ServerWebExchange exchange) {
        documentService.deleteDocumentAndChunks(id);
        return Result.success("删除成功", null);
    }
}
