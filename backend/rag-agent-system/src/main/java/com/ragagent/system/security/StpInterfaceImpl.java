package com.ragagent.system.security;

import cn.dev33.satoken.stp.StpInterface;
import com.ragagent.system.entity.SysRole;
import com.ragagent.system.mapper.SysMenuMapper;
import com.ragagent.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        // 管理员拥有最高权限
        if (userId == 1L) {
            List<String> adminPerms = new ArrayList<>();
            adminPerms.add("*:*:*");
            return adminPerms;
        }
        return sysMenuMapper.selectPermsByUserId(userId);
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (如 admin, kb_admin, common)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(SysRole::getRoleKey).collect(Collectors.toList());
    }
}
