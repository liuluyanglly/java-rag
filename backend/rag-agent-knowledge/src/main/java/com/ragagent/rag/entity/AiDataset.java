package com.ragagent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_dataset")
@Schema(name = "AiDataset", description = "企业知识库数据集（支持逻辑/物理双轨隔离策略）")
public class AiDataset implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "知识库唯一ID，自增主键。新增时无需传入", example = "1")
    private Long id;

    @Schema(description = "知识库名称", example = "企业研发规范库")
    private String name;

    @Schema(description = "知识库定位与业务背景描述")
    private String description;

    @Schema(description = "知识库封面图标URL")
    private String avatar;

    @Schema(description = "绑定的向量 Embedding 模型编码", example = "text-embedding-3-small")
    private String embeddingModel;

    @Schema(description = "文本切片分块 Token 最大限制值", example = "500")
    private Integer chunkSize;

    @Schema(description = "分块重叠滑窗 Token 大小，用于保留跨块上下文", example = "50")
    private Integer chunkOverlap;

    @Schema(description = "收录的文档文件总数，由系统统计维护", example = "12")
    private Integer docCount;

    @Schema(description = "已切片分块生成的段落总数，由系统统计维护", example = "860")
    private Integer chunkCount;

    @Schema(description = "是否全员公开（true-全员公开, false-按角色白名单授权）", example = "true")
    private Boolean isPublic;

    /**
     * 隔离类型: LOGICAL(逻辑隔离/元数据过滤), PHYSICAL(物理隔离/专属Schema或数据源)
     */
    @Builder.Default
    @Schema(description = "隔离策略（LOGICAL-逻辑共享表按元数据过滤, PHYSICAL-物理专属独立Schema）",
            example = "LOGICAL", allowableValues = {"LOGICAL", "PHYSICAL"}, defaultValue = "LOGICAL")
    private String isolationType = "LOGICAL";

    /**
     * 物理隔离下的专属 Schema 名称 (如: isolated_sec_01)
     */
    @Schema(description = "物理隔离模式下的专属 PostgreSQL Schema 名称，仅 isolationType=PHYSICAL 时有效",
            example = "isolated_sec_01")
    private String isolatedSchema;

    /**
     * 安全密级: 1-普通公开, 2-部门内部, 3-核心机密
     */
    @Builder.Default
    @Schema(description = "数据安全密级（1-普通公开, 2-内部涉密, 3-核心高密）。3 级强制走私有化本地模型，禁止出网",
            example = "1", allowableValues = {"1", "2", "3"}, defaultValue = "1")
    private Integer securityLevel = 1;

    @Schema(description = "创建人用户ID，由服务端从当前登录态写入", example = "1")
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后更新时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    @Schema(description = "授权可访问该知识库的角色ID集合，仅入参（新增/修改时提交，覆盖式全量重设）", example = "[1, 2]")
    private List<Long> authorizedRoleIds;
}
