package com.ragagent.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.agent.entity.AiAgent;

import java.util.List;

public interface AiAgentService extends IService<AiAgent> {

    Page<AiAgent> pageAgents(Integer pageNum, Integer pageSize, String name);

    List<AiAgent> listActiveAgents();

    void createAgent(AiAgent agent, Long userId);

    void updateAgent(AiAgent agent);

    void deleteAgent(Long id);
}
