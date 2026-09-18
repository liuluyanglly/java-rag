package com.ragagent.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.system.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {
    SysUser getByUsername(String username);
    String login(String username, String password);
    void assignRoles(Long userId, List<Long> roleIds);
}
