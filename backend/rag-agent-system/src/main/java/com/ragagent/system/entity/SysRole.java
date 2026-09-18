package com.ragagent.system.entity;

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
@TableName("sys_role")
@Schema(name = "SysRole", description = "系统角色权限")
public class SysRole implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "角色ID，自增主键。1 为超级管理员角色，不可删除", example = "1")
    private Long roleId;

    @Schema(description = "角色中文展示名称", example = "知识运营工程师")
    private String roleName;

    @Schema(description = "角色权限唯一字符标识（如: admin, user）", example = "admin")
    private String roleKey;

    @Schema(description = "展示排序次序，升序排列", example = "1")
    private Integer roleSort;

    @Schema(description = "角色状态（0-正常, 1-停用）", example = "0", allowableValues = {"0", "1"})
    private String status;

    @Schema(description = "逻辑删除标志（0-正常存在, 2-已删除），由框架维护，无需传入", example = "0")
    private String delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "角色创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "角色修改时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    @Schema(description = "待授权的菜单ID集合，仅入参（新增/修改角色时提交，覆盖式全量重设）", example = "[1, 2, 3]")
    private List<Long> menuIds;
}
