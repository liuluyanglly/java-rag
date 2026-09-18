package com.ragagent.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.system.entity.SysRole;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface SysRoleService extends IService<SysRole> {
    List<SysRole> getRolesByUserId(Long userId);
    void assignMenus(Long roleId, List<Long> menuIds);
    Page<SysRole> pageRoles(Integer pageNum, Integer pageSize, String roleName);
    List<SysRole> listActiveRoles();
    void createRole(SysRole role);
    void updateRole(SysRole role);
    void deleteRole(Long roleId);
}
