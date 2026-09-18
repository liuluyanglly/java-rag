package com.ragagent.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.common.result.Result;
import com.ragagent.system.entity.SysRole;
import com.ragagent.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "分页查询角色列表", description = "按 roleSort 升序排列")
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public Result<Page<SysRole>> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "角色名称，模糊匹配，为空则不过滤", example = "管理员")
            @RequestParam(required = false) String roleName) {

        Page<SysRole> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .orderByAsc(SysRole::getRoleSort);

        return Result.success(sysRoleService.page(page, wrapper));
    }

    @Operation(summary = "获取所有启用角色列表",
            description = "不分页，仅返回 status=0（正常）的角色，供用户授权与知识库白名单下拉选择使用")
    @GetMapping("/all")
    public Result<List<SysRole>> all() {
        return Result.success(sysRoleService.list(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, "0")));
    }

    @Operation(summary = "新增角色", description = "menuIds 非空时同步写入菜单权限授权关系")
    @SaCheckPermission("system:role:add")
    @PostMapping
    public Result<Void> add(@RequestBody SysRole role) {
        sysRoleService.save(role);
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            sysRoleService.assignMenus(role.getRoleId(), role.getMenuIds());
        }
        return Result.success("角色创建成功", null);
    }

    @Operation(summary = "修改角色",
            description = "menuIds 非 null 时覆盖式重设菜单授权，传 null 表示不调整授权")
    @SaCheckPermission("system:role:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody SysRole role) {
        sysRoleService.updateById(role);
        if (role.getMenuIds() != null) {
            sysRoleService.assignMenus(role.getRoleId(), role.getMenuIds());
        }
        return Result.success("角色修改成功", null);
    }

    @Operation(summary = "删除角色", description = "roleId=1 为超级管理员角色，禁止删除")
    @SaCheckPermission("system:role:remove")
    @DeleteMapping("/{roleId}")
    public Result<Void> remove(
            @Parameter(description = "角色ID", example = "2", required = true)
            @PathVariable Long roleId) {
        if (roleId == 1L) {
            return Result.error("超级管理员角色不可删除");
        }
        sysRoleService.removeById(roleId);
        return Result.success("删除成功", null);
    }
}
