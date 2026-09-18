package com.ragagent.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.system.entity.SysMenu;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    List<SysMenu> getMenuTreeByUserId(Long userId);
    List<SysMenu> buildTree(List<SysMenu> menus);
}
