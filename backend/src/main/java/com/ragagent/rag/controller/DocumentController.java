package com.ragagent.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.common.result.Result;
import com.ragagent.rag.entity.AiDocument;
import com.ragagent.rag.entity.AiDocumentChunk;
import com.ragagent.rag.mapper.AiDocumentChunkMapper;
import com.ragagent.rag.mapper.AiDocumentMapper;
import com.ragagent.rag.service.RagIndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import org.springframework.jdbc.core.JdbcTemplate;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.entity.AiDataset;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "知识库文档管理")
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiDatasetMapper datasetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RagIndexingService indexingService;

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
            @RequestParam(defaultValue = "10") Integer pageSize) {

        Page<AiDocument> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDatasetId, datasetId)
                .orderByDesc(AiDocument::getCreateTime);

        return Result.success(documentMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "上传文档并触发异步切片",
            description = "multipart/form-data 上传。接口立即返回状态为 PENDING 的文档记录，"
                    + "切片解析在后台异步执行，可轮询文档列表的 status 字段获取进度")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schemaProperties = @SchemaProperty(name = "file",
                            schema = @Schema(type = "string", format = "binary",
                                    description = "待解析的文档文件，支持 pdf / docx / txt / md，单文件上限 50MB"))))
    @SaCheckPermission("ai:dataset:edit")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<AiDocument>> upload(
            @Parameter(description = "归属知识库ID", example = "1", required = true)
            @RequestParam("datasetId") Long datasetId,
            @Parameter(hidden = true)
            @RequestPart("file") FilePart filePart) {

        String originalFilename = filePart.filename();
        String ext = FileUtil.extName(originalFilename);
        String subPath = "uploads/" + datasetId + "/";
        File dir = new File(subPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String saveFileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File targetFile = new File(dir, saveFileName);

        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            userId = 1L;
        }
        final Long currentUserId = userId;

        return filePart.transferTo(targetFile.toPath())
                .then(Mono.fromCallable(() -> {
                    AiDocument doc = AiDocument.builder()
                            .datasetId(datasetId)
                            .name(originalFilename)
                            .filePath(targetFile.getAbsolutePath())
                            .fileSize(targetFile.length())
                            .fileType(ext)
                            .status("PENDING")
                            .createdBy(currentUserId)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                    documentMapper.insert(doc);

                    // 触发异步切片索引流水线
                    indexingService.indexDocumentAsync(doc.getId());

                    return Result.success("文档上传成功，正在后台切片解析中", doc);
                }));
    }

    @Operation(summary = "查询文档切片详情",
            description = "按段落自然顺序（chunkIndex 升序）返回指定文档的切片明细，用于人工校验切片质量")
    @GetMapping("/chunks")
    public Result<Page<AiDocumentChunk>> chunks(
            @Parameter(description = "文档ID", example = "1", required = true)
            @RequestParam Long documentId,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize) {

        Page<AiDocumentChunk> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiDocumentChunk> wrapper = new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getDocumentId, documentId)
                .orderByAsc(AiDocumentChunk::getChunkIndex);

        return Result.success(chunkMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "重新触发切片",
            description = "重新提交异步切片索引任务，用于解析失败后重试或切片参数调整后重建")
    @PostMapping("/{id}/reindex")
    public Result<Void> reindex(
            @Parameter(description = "文档ID", example = "1", required = true)
            @PathVariable Long id) {
        indexingService.indexDocumentAsync(id);
        return Result.success("已提交重新切片任务", null);
    }

    @Operation(summary = "删除文档",
            description = "删除文档记录并级联清理其全部切片分块与向量索引")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "文档ID", example = "1", required = true)
            @PathVariable Long id) {
        AiDocument doc = documentMapper.selectById(id);
        if (doc != null) {
            Long datasetId = doc.getDatasetId();
            documentMapper.deleteById(id);
            chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>().eq(AiDocumentChunk::getDocumentId, id));

            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", String.valueOf(id));
                log.info("已级联清理 vector_store 中文档 [{}] 的全部向量记录", id);
            } catch (Exception e) {
                log.warn("级联清理向量库异常: {}", e.getMessage());
            }

            if (datasetId != null) {
                AiDataset dataset = datasetMapper.selectById(datasetId);
                if (dataset != null) {
                    Long actualDocCount = documentMapper.selectCount(new LambdaQueryWrapper<AiDocument>()
                            .eq(AiDocument::getDatasetId, datasetId));
                    List<AiDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                            .eq(AiDocument::getDatasetId, datasetId));
                    int actualChunkTotal = docs.stream()
                            .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                            .sum();
                    dataset.setDocCount(actualDocCount.intValue());
                    dataset.setChunkCount(actualChunkTotal);
                    datasetMapper.updateById(dataset);
                }
            }
        }
        return Result.success("删除成功", null);
    }
}
