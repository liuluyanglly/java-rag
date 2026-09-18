package com.ragagent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_document_chunk")
@Schema(name = "AiDocumentChunk", description = "文档段落切片分块")
public class AiDocumentChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "切片分块ID，自增主键", example = "1")
    private Long id;

    @Schema(description = "归属知识库ID", example = "1")
    private Long datasetId;

    @Schema(description = "所属文档ID", example = "1")
    private Long documentId;

    @Schema(description = "在文档中的相对段落自然顺序号，从 0 开始", example = "0")
    private Integer chunkIndex;

    @Schema(description = "段落正文实际切片文本内容")
    private String content;

    @Schema(description = "该段切片所包含的 Token 计算值", example = "500")
    private Integer tokenCount;

    /**
     * 元数据字符串 (JSON格式，保存文档名称、权限标签等)
     */
    @Schema(description = "切片附加元数据，JSON 字符串，含 dataset_id / accessible_roles / security_level 等检索过滤标签",
            example = "{\"dataset_id\":1,\"accessible_roles\":[1,2],\"security_level\":1}")
    private String metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "切片入库持久化时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;
}
