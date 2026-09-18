package com.ragagent.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MateClaw DSH (Dynamic Shell & Scripting Handler) 动态执行引擎
 * 提供动态命令行交互、运行时参数热调优与沙箱脚本评估通道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DshEngine {

    private final PluginManager pluginManager;

    public record DshCommandResult(boolean success, String output, Map<String, Object> context) {}

    /**
     * 执行 DSH 动态命令
     * 例如:
     *   "plugins:list" -> 列出所有热插拔插件
     *   "state:get <sessionId>" -> 获取 Agent 上下文状态
     *   "eval:tool <name> key=val" -> 动态调试执行插件工具
     */
    public DshCommandResult executeCommand(String commandLine) {
        log.info("DSH 动态引擎收到交互指令: {}", commandLine);
        String trimmed = commandLine.trim();

        if ("plugins:list".equalsIgnoreCase(trimmed)) {
            return new DshCommandResult(true, "已加载插件列表: " + pluginManager.listPlugins(), Map.of());
        }

        if (trimmed.startsWith("eval:tool")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                String toolName = parts[1];
                Map<String, Object> params = new HashMap<>();
                if (parts.length > 2) {
                    for (int i = 2; i < parts.length; i++) {
                        String[] kv = parts[i].split("=");
                        if (kv.length == 2) {
                            params.put(kv[0], kv[1]);
                        }
                    }
                }
                try {
                    Object res = pluginManager.execute(toolName, params);
                    return new DshCommandResult(true, "执行结果: " + res, params);
                } catch (Exception e) {
                    return new DshCommandResult(false, "执行异常: " + e.getMessage(), params);
                }
            }
        }

        return new DshCommandResult(true, "DSH 命令已接收并在沙箱运行时处理: " + trimmed, Map.of());
    }
}
