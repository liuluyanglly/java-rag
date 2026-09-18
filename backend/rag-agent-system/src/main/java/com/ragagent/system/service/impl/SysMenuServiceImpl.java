package com.ragagent.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.system.entity.SysMenu;
import com.ragagent.system.mapper.SysMenuMapper;
import com.ragagent.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<SysMenu> getMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        if (userId == 1L) {
            // 管理员直接查询全部
            menus = list(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, "0")
                    .in(SysMenu::getMenuType, "M", "C")
                    .orderByAsc(SysMenu::getOrderNum));
        } else {
            menus = sysMenuMapper.selectMenuTreeByUserId(userId);
        }
        return buildTree(menus);
    }

    @Override
    public List<SysMenu> buildTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<>();
        if (menus == null || menus.isEmpty()) {
            return returnList;
        }

        Map<Long, List<SysMenu>> parentMap = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));

        for (SysMenu menu : menus) {
            menu.setChildren(parentMap.getOrDefault(menu.getMenuId(), new ArrayList<>()));
            if (menu.getParentId() == null || menu.getParentId() == 0L) {
                returnList.add(menu);
            }
        }
        return returnList;
    }
}
