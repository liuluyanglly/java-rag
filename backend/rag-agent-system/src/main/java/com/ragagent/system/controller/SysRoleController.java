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
        return Result.success(sysRoleService.pageRoles(pageNum, pageSize, roleName));
    }

    @Operation(summary = "获取所有启用角色列表",
            description = "不分页，仅返回 status=0（正常）的角色，供用户授权与知识库白名单下拉选择使用")
    @GetMapping("/all")
    public Result<List<SysRole>> all() {
        return Result.success(sysRoleService.listActiveRoles());
    }

    @Operation(summary = "新增角色", description = "menuIds 非空时同步写入菜单权限授权关系")
    @SaCheckPermission("system:role:add")
    @PostMapping
    public Result<Void> add(@RequestBody SysRole role) {
        sysRoleService.createRole(role);
        return Result.success("角色创建成功", null);
    }

    @Operation(summary = "修改角色", description = "menuIds 非 null 时覆盖式重设菜单权限，传 null 表示不调整授权")
    @SaCheckPermission("system:role:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody SysRole role) {
        sysRoleService.updateRole(role);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除角色")
    @SaCheckPermission("system:role:remove")
    @DeleteMapping("/{roleId}")
    public Result<Void> remove(
            @Parameter(description = "角色ID", example = "2", required = true)
            @PathVariable Long roleId) {
        sysRoleService.deleteRole(roleId);
        return Result.success("删除成功", null);
    }
}
