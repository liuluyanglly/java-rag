package com.ragagent.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.common.result.Result;
import com.ragagent.system.entity.SysUser;
import com.ragagent.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询用户列表",
            description = "按创建时间倒序。出参不含 password 字段")
    @SaCheckPermission("system:user:list")
    @GetMapping("/list")
    public Result<Page<SysUser>> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "登录账号，模糊匹配，为空则不过滤", example = "admin")
            @RequestParam(required = false) String username,
            @Parameter(description = "帐号状态精确匹配（0-正常, 1-停用），为空则不过滤", example = "0",
                    schema = @Schema(allowableValues = {"0", "1"}))
            @RequestParam(required = false) String status) {

        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);

        return Result.success(sysUserService.page(page, wrapper));
    }

    @Operation(summary = "新增用户",
            description = "username 重复时返回错误码 500。password 不传默认 123456，落库前统一 BCrypt 加密；"
                    + "roleIds 非空时同步分配角色")
    @SaCheckPermission("system:user:add")
    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        if (sysUserService.getByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword("123456"); // 默认初始密码
        }
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        sysUserService.save(user);

        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            sysUserService.assignRoles(user.getUserId(), user.getRoleIds());
        }
        return Result.success("用户创建成功", null);
    }

    @Operation(summary = "修改用户",
            description = "password 传空表示不修改密码；roleIds 非 null 时覆盖式重设角色，传 null 表示不调整角色")
    @SaCheckPermission("system:user:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody SysUser user) {
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(BCrypt.hashpw(user.getPassword()));
        } else {
            user.setPassword(null); // 不修改密码
        }
        sysUserService.updateById(user);
        if (user.getRoleIds() != null) {
            sysUserService.assignRoles(user.getUserId(), user.getRoleIds());
        }
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除用户", description = "userId=1 为初始管理员账号，禁止删除")
    @SaCheckPermission("system:user:remove")
    @DeleteMapping("/{userId}")
    public Result<Void> remove(
            @Parameter(description = "用户ID", example = "2", required = true)
            @PathVariable Long userId) {
        if (userId == 1L) {
            return Result.error("不能删除初始管理员账号");
        }
        sysUserService.removeById(userId);
        return Result.success("删除成功", null);
    }
}
