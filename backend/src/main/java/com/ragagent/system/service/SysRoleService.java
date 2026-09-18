package com.ragagent.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.system.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {
    List<SysRole> getRolesByUserId(Long userId);
    void assignMenus(Long roleId, List<Long> menuIds);
}
