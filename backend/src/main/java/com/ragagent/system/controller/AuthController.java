package com.ragagent.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ragagent.common.result.Result;
import com.ragagent.system.entity.SysMenu;
import com.ragagent.system.entity.SysRole;
import com.ragagent.system.entity.SysUser;
import com.ragagent.system.service.SysMenuService;
import com.ragagent.system.service.SysRoleService;
import com.ragagent.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "认证授权中心")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;

    @Data
    @Schema(name = "LoginBody", description = "账号密码登录入参")
    public static class LoginBody {

        @Schema(description = "登录账号", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        private String username;

        @Schema(description = "登录密码明文", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
    }

    @Operation(summary = "用户登录",
            description = "校验账号密码并签发 Sa-Token 会话令牌。出参 data 为 Map：token-会话令牌，tokenName-令牌请求头名称。"
                    + "后续接口需将 token 放入该请求头。此接口无需鉴权。")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginBody body) {
        String token = sysUserService.login(body.getUsername(), body.getPassword());
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("tokenName", StpUtil.getTokenName());
        return Result.success("登录成功", map);
    }

    @Operation(summary = "获取当前用户信息及角色权限",
            description = "出参 data 为 Map：user-用户基本资料(SysUser)，roles-已授予角色列表(SysRole 数组)，permissions-权限码字符串数组")
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserService.getById(userId);
        List<SysRole> roles = sysRoleService.getRolesByUserId(userId);
        List<String> permissions = StpUtil.getPermissionList();

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("roles", roles);
        data.put("permissions", permissions);
        return Result.success(data);
    }

    @Operation(summary = "获取当前用户动态路由菜单",
            description = "按当前用户角色过滤后的菜单树，children 字段递归嵌套子菜单")
    @GetMapping("/routers")
    public Result<List<SysMenu>> getRouters() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SysMenu> menuTree = sysMenuService.getMenuTreeByUserId(userId);
        return Result.success(menuTree);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success("退出成功", null);
    }
}
