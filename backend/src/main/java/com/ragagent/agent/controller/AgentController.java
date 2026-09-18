package com.ragagent.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.agent.entity.AiAgent;
import com.ragagent.agent.mapper.AiAgentMapper;
import com.ragagent.common.result.Result;
import com.ragagent.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Agent 智能体管理")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AiAgentMapper agentMapper;

    @Operation(summary = "分页查询智能体列表",
            description = "管理端全量分页列表，按创建时间倒序")
    @GetMapping("/list")
    public Result<Page<AiAgent>> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "智能体名称，模糊匹配，为空则不过滤", example = "研发")
            @RequestParam(required = false) String name,
            ServerWebExchange exchange) {

        Page<AiAgent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), AiAgent::getName, name)
                .orderByDesc(AiAgent::getCreateTime);

        return Result.success(agentMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "获取当前可见的可用智能体",
            description = "用户端助手广场使用，仅返回 status=0（正常启用）的智能体")
    @GetMapping("/active")
    public Result<List<AiAgent>> getActiveAgents(ServerWebExchange exchange) {
        return Result.success(agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getStatus, "0")
                .orderByDesc(AiAgent::getCreateTime)));
    }

    @Operation(summary = "获取智能体详情",
            description = "返回完整配置，含 System Prompt、模型参数、挂载知识库与工具集")
    @GetMapping("/{id}")
    public Result<AiAgent> getById(
            @Parameter(description = "智能体ID", example = "1", required = true)
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return Result.success(agentMapper.selectById(id));
    }

    @Operation(summary = "新增智能体",
            description = "入参 id / createdBy / createTime / updateTime 由服务端生成，无需传入")
    @SaCheckPermission("ai:agent:add")
    @PostMapping
    public Result<Void> add(@RequestBody AiAgent agent, ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserId(exchange);
        agent.setCreatedBy(userId);
        agent.setCreateTime(LocalDateTime.now());
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.insert(agent);
        return Result.success("智能体创建成功", null);
    }

    @Operation(summary = "修改智能体", description = "按 id 更新智能体配置，未传字段保持原值")
    @SaCheckPermission("ai:agent:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody AiAgent agent, ServerWebExchange exchange) {
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        return Result.success("智能体更新成功", null);
    }

    @Operation(summary = "删除智能体")
    @SaCheckPermission("ai:agent:remove")
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @Parameter(description = "智能体ID", example = "1", required = true)
            @PathVariable Long id,
            ServerWebExchange exchange) {
        agentMapper.deleteById(id);
        return Result.success("删除成功", null);
    }
}
