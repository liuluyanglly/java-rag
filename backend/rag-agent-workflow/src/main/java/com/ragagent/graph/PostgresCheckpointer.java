package com.ragagent.graph;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于全栈 PostgreSQL 的状态图检查点持久化实现
 * 支持断点续跑、崩溃无损恢复与历史轨迹回溯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresCheckpointer {

    private final JdbcTemplate jdbcTemplate;

    public void saveCheckpoint(String threadId, String nodeName, GraphState state) {
        try {
            String checkpointId = IdUtil.simpleUUID();
            String stateJson = JSONUtil.toJsonStr(state);
            String sql = "INSERT INTO graph_checkpoint (checkpoint_id, thread_id, node_name, state_json) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, checkpointId, threadId, nodeName, stateJson);
            log.debug("StateGraph 检查点落库成功: threadId={}, node={}", threadId, nodeName);
        } catch (Exception e) {
            log.warn("检查点落库告警 (可降级): {}", e.getMessage());
        }
    }

    public GraphState loadLatestCheckpoint(String threadId) {
        try {
            String sql = "SELECT state_json FROM graph_checkpoint WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1";
            String json = jdbcTemplate.queryForObject(sql, String.class, threadId);
            if (json != null) {
                return JSONUtil.toBean(json, GraphState.class);
            }
        } catch (Exception e) {
            log.warn("未查到历史检查点: threadId={}", threadId);
        }
        return null;
    }
}
