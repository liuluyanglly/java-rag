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
@TableName("ai_document")
@Schema(name = "AiDocument", description = "知识文档原件资产登记信息")
public class AiDocument implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "文档ID，自增主键", example = "1")
    private Long id;

    @Schema(description = "归属知识库ID", example = "1")
    private Long datasetId;

    @Schema(description = "文档原始文件名", example = "研发管理规范V2.pdf")
    private String name;

    @Schema(description = "物理存储相对路径或 OSS ObjectKey")
    private String filePath;

    @Schema(description = "文件实际体积（字节）", example = "1048576")
    private Long fileSize;

    @Schema(description = "文件扩展名类型", example = "pdf", allowableValues = {"pdf", "docx", "doc", "txt", "md"})
    private String fileType;

    /**
     * 状态: PENDING(等待处理), PARSING(切片中), COMPLETED(已完成), FAILED(失败)
     */
    @Schema(description = "解析切片处理状态（PENDING-排队中, PARSING-处理中, COMPLETED-已就绪, FAILED-失败）",
            example = "COMPLETED", allowableValues = {"PENDING", "PARSING", "COMPLETED", "FAILED"})
    private String status;

    @Schema(description = "解析拆分出的切片块总数", example = "86")
    private Integer chunkCount;

    @Schema(description = "文档预估消耗 Token 总数", example = "43000")
    private Integer tokenCount;

    @Schema(description = "解析失败的异常原因描述，status=FAILED 时有值")
    private String errorMsg;

    @Schema(description = "上传人用户ID", example = "1")
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "文档上传入库时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "解析状态修改时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;
}
