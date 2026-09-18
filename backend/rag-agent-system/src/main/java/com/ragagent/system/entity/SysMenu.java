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
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_menu")
@Schema(name = "SysMenu", description = "系统菜单与功能权限字典（树形结构）")
public class SysMenu implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "菜单ID，自增主键", example = "1")
    private Long menuId;

    @Schema(description = "菜单显示名称", example = "知识库管理")
    private String menuName;

    @Schema(description = "父级菜单ID（0 表示顶级根菜单）", example = "0")
    private Long parentId;

    @Schema(description = "同级显示顺序编号，升序排列", example = "1")
    private Integer orderNum;

    @Schema(description = "前端路由跳转路径URL", example = "/dataset")
    private String path;

    @Schema(description = "前端对应 Vue/React 组件映射路径", example = "dataset/index")
    private String component;

    @Schema(description = "是否为外部链接内嵌（0-否, 1-是）", example = "0", allowableValues = {"0", "1"})
    private Integer isFrame;

    /**
     * 菜单类型（M目录 C菜单 F按钮）
     */
    @Schema(description = "菜单类型（M-目录, C-菜单, F-按钮操作权限）", example = "C", allowableValues = {"M", "C", "F"})
    private String menuType;

    @Schema(description = "导航菜单是否可见（0-显示, 1-隐藏）", example = "0", allowableValues = {"0", "1"})
    private String visible;

    @Schema(description = "菜单启用状态（0-正常, 1-停用）", example = "0", allowableValues = {"0", "1"})
    private String status;

    /**
     * 权限标识符，如 ai:dataset:list, system:user:add
     */
    @Schema(description = "后端API鉴权标识，对应 Sa-Token @SaCheckPermission 的权限码", example = "ai:dataset:list")
    private String perms;

    @Schema(description = "菜单展示图标名称", example = "database")
    private String icon;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "菜单创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "菜单修改时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    /**
     * 子菜单树
     */
    @TableField(exist = false)
    @Builder.Default
    @Schema(description = "子菜单集合，由后端递归组装为树形结构后返回")
    private List<SysMenu> children = new ArrayList<>();
}
