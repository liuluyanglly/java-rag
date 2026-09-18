package com.ragagent.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@TableName("sys_user")
@Schema(name = "SysUser", description = "系统用户基础信息")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID，自增主键", example = "1")
    private Long userId;

    @Schema(description = "登录账号唯一用户名", example = "admin")
    private String username;

    @Schema(description = "用户显示昵称", example = "超级管理员")
    private String nickName;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "登录密码明文，仅入参使用；出参永不返回，落库为 BCrypt 密文。新增用户不传时默认 123456",
            example = "123456", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Schema(description = "用户联系电子邮箱", example = "admin@ragagent.com")
    private String email;

    @Schema(description = "用户联系手机号码", example = "13800000000")
    private String phone;

    @Schema(description = "用户头像存储URL")
    private String avatar;

    @Schema(description = "帐号状态（0-正常, 1-停用）", example = "0", allowableValues = {"0", "1"})
    private String status;

    @Schema(description = "逻辑删除标志（0-正常存在, 2-已删除），由框架维护，无需传入", example = "0")
    private String delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "账号注册创建时间", example = "2026-09-16 14:47:05")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "账号最后修改时间", example = "2026-09-16 14:47:05")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    @Schema(description = "该用户已授予的角色列表，仅出参（查询用户详情时回填）")
    private List<SysRole> roles;

    @TableField(exist = false)
    @Schema(description = "待分配的角色ID集合，仅入参（新增/修改用户时提交，覆盖式全量重设）", example = "[1, 2]")
    private List<Long> roleIds;
}
