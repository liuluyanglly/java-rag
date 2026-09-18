package com.ragagent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.agent.entity.AiAgent;
import com.ragagent.agent.mapper.AiAgentMapper;
import com.ragagent.agent.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgent> implements AiAgentService {

    @Override
    public Page<AiAgent> pageAgents(Integer pageNum, Integer pageSize, String name) {
        Page<AiAgent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), AiAgent::getName, name)
                .orderByDesc(AiAgent::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public List<AiAgent> listActiveAgents() {
        return list(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getStatus, "0")
                .orderByDesc(AiAgent::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAgent(AiAgent agent, Long userId) {
        agent.setCreatedBy(userId);
        agent.setCreateTime(LocalDateTime.now());
        agent.setUpdateTime(LocalDateTime.now());
        save(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAgent(AiAgent agent) {
        agent.setUpdateTime(LocalDateTime.now());
        updateById(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long id) {
        removeById(id);
    }
}
