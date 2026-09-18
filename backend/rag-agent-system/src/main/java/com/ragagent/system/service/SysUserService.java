package com.ragagent.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.system.entity.SysUser;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface SysUserService extends IService<SysUser> {
    SysUser getByUsername(String username);
    String login(String username, String password);
    void assignRoles(Long userId, List<Long> roleIds);
    Page<SysUser> pageUsers(Integer pageNum, Integer pageSize, String username, String status);
    void createUser(SysUser user);
    void updateUser(SysUser user);
    void deleteUser(Long userId);
}
