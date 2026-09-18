package com.ragagent.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MateClaw 可插拔插件管理中心 (支持本地 Tool、脚本工具与 MCP 协议扩展)
 */
@Slf4j
@Component
public class PluginManager {

    public record PluginMeta(String name, String description, String version, String category) {}

    private final Map<String, PluginMeta> pluginRegistry = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String, Object>, Object>> executionRegistry = new ConcurrentHashMap<>();

    public void registerPlugin(PluginMeta meta, Function<Map<String, Object>, Object> executor) {
        pluginRegistry.put(meta.name(), meta);
        executionRegistry.put(meta.name(), executor);
        log.info("MateClaw 插件热插拔挂载成功: [{}] - {}", meta.name(), meta.description());
    }

    public void unregisterPlugin(String pluginName) {
        pluginRegistry.remove(pluginName);
        executionRegistry.remove(pluginName);
        log.info("MateClaw 插件已卸载: [{}]", pluginName);
    }

    public Object execute(String pluginName, Map<String, Object> params) {
        Function<Map<String, Object>, Object> executor = executionRegistry.get(pluginName);
        if (executor == null) {
            throw new IllegalArgumentException("未找到已注册的插件工具: " + pluginName);
        }
        return executor.apply(params);
    }

    public List<PluginMeta> listPlugins() {
        return new ArrayList<>(pluginRegistry.values());
    }
}
