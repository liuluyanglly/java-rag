package com.ragagent.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.common.result.Result;
import com.ragagent.system.entity.SysMenu;
import com.ragagent.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @Operation(summary = "获取菜单树列表",
            description = "返回全量菜单并组装为树形结构，children 字段递归嵌套子菜单，同级按 orderNum 升序")
    @SaCheckPermission("system:menu:list")
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        List<SysMenu> list = sysMenuService.list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getOrderNum));
        return Result.success(sysMenuService.buildTree(list));
    }

    @Operation(summary = "新增菜单")
    @SaCheckPermission("system:menu:add")
    @PostMapping
    public Result<Void> add(@RequestBody SysMenu menu) {
        sysMenuService.save(menu);
        return Result.success("菜单添加成功", null);
    }

    @Operation(summary = "修改菜单")
    @SaCheckPermission("system:menu:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody SysMenu menu) {
        sysMenuService.updateById(menu);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除菜单", description = "存在子菜单时拒绝删除，需先删除全部下级菜单")
    @SaCheckPermission("system:menu:remove")
    @DeleteMapping("/{menuId}")
    public Result<Void> remove(
            @Parameter(description = "菜单ID", example = "2", required = true)
            @PathVariable Long menuId) {
        long childCount = sysMenuService.count(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
        if (childCount > 0) {
            return Result.error("存在子菜单，不允许直接删除");
        }
        sysMenuService.removeById(menuId);
        return Result.success("删除成功", null);
    }
}
