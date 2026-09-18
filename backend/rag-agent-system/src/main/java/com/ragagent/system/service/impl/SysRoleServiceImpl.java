package com.ragagent.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.system.entity.SysRole;
import com.ragagent.system.entity.SysRoleMenu;
import com.ragagent.system.mapper.SysRoleMapper;
import com.ragagent.system.mapper.SysRoleMenuMapper;
import com.ragagent.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysRole> getRolesByUserId(Long userId) {
        return sysRoleMapper.selectRolesByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                sysRoleMenuMapper.insert(SysRoleMenu.builder().roleId(roleId).menuId(menuId).build());
            }
        }
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysRole> pageRoles(
            Integer pageNum, Integer pageSize, String roleName) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysRole> page = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(org.springframework.util.StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .orderByAsc(SysRole::getRoleSort);
        return page(page, wrapper);
    }

    @Override
    public List<SysRole> listActiveRoles() {
        return list(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, "0"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(SysRole role) {
        save(role);
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            assignMenus(role.getRoleId(), role.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRole role) {
        updateById(role);
        if (role.getMenuIds() != null) {
            assignMenus(role.getRoleId(), role.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        removeById(roleId);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
    }
}
