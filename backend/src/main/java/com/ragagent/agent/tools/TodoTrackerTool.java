package com.ragagent.agent.tools;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 结构化待办与多步任务追踪工具 (借鉴 Spring-AI-Agent-Utils 之 TodoWriteTool)
 * 供大模型智能体在规划与执行多步骤复杂工作流时进行阶段记录与透明度公示。
 */
@Slf4j
@Component
public class TodoTrackerTool {

    private final Map<String, List<TaskItem>> sessionTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGen = new AtomicInteger(1);

    @Tool(description = "为当前工作流或会话添加一个新的待办子任务，返回已创建的任务详情")
    public TaskItem addTask(String sessionId, String title, String description) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        int id = idGen.getAndIncrement();
        TaskItem item = new TaskItem(id, title, description, TaskStatus.PENDING, System.currentTimeMillis());
        sessionTasks.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
        log.info("[TodoTracker] 会话 {} 新增任务 #{}: {}", sid, id, title);
        return item;
    }

    @Tool(description = "更新指定会话下某个子任务的状态。可选状态：PENDING (待处理), IN_PROGRESS (执行中), COMPLETED (已完成), FAILED (失败)")
    public String updateTaskStatus(String sessionId, int taskId, String statusStr) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        List<TaskItem> list = sessionTasks.get(sid);
        if (list == null || list.isEmpty()) {
            return "未找到会话 " + sid + " 下的任何任务";
        }
        for (TaskItem item : list) {
            if (item.getId() == taskId) {
                try {
                    TaskStatus status = TaskStatus.valueOf(statusStr.toUpperCase().trim());
                    item.setStatus(status);
                    log.info("[TodoTracker] 会话 {} 任务 #{} 状态更新为 {}", sid, taskId, status);
                    return "任务 #" + taskId + " 状态成功更新为 " + status;
                } catch (IllegalArgumentException e) {
                    return "未知状态类型: " + statusStr + "，支持的状态为: PENDING, IN_PROGRESS, COMPLETED, FAILED";
                }
            }
        }
        return "未找到任务ID为 " + taskId + " 的任务";
    }

    @Tool(description = "获取当前会话下所有的任务清单列表及其完成进度")
    public List<TaskItem> listTasks(String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        return sessionTasks.getOrDefault(sid, Collections.emptyList());
    }

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private int id;
        private String title;
        private String description;
        private TaskStatus status;
        private long createdAt;
    }
}
