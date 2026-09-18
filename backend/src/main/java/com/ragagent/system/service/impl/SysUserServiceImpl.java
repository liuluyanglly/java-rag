package com.ragagent.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.system.entity.SysUser;
import com.ragagent.system.entity.SysUserRole;
import com.ragagent.system.mapper.SysUserMapper;
import com.ragagent.system.mapper.SysUserRoleMapper;
import com.ragagent.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public SysUser getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    @Override
    public String login(String username, String password) {
        SysUser user = getByUsername(username);
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }
        if ("1".equals(user.getStatus())) {
            throw new RuntimeException("账号已被停用");
        }
        // 校验密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // Sa-Token 登录
        StpUtil.login(user.getUserId());
        return StpUtil.getTokenValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        // 先删除原有角色
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                sysUserRoleMapper.insert(SysUserRole.builder().userId(userId).roleId(roleId).build());
            }
        }
    }
}
