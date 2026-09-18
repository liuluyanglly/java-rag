package com.ragagent.agent.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Agent 运行时环境自动感知组件 (借鉴 Spring-AI-Agent-Utils 核心思想)
 * 为大语言模型或智能体注入真实的宿主环境上下文，避免时空幻觉与环境误判：
 * 1. 当前操作系统与系统架构 (os.name, os.arch)
 * 2. 真实系统时间 (YYYY-MM-DD HH:mm:ss)
 * 3. 运行工作目录 (user.dir)
 * 4. JVM 运行时与内存状态
 */
@Component
public class AgentEnvironment {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE);

    /**
     * 生成供 Agent System Prompt 注入的格式化环境描述文本
     */
    public String getEnvironmentContext() {
        String os = System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")";
        String javaVer = System.getProperty("java.version");
        String workDir = System.getProperty("user.dir");
        String currentTime = LocalDateTime.now().format(FORMATTER);
        long freeMemMb = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMemMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);

        return String.format("""
                [系统运行时环境]
                - 当前时间: %s
                - 操作系统: %s
                - Java 版本: %s
                - 工作目录: %s
                - 内存状态: 可用 %dMB / 总分配 %dMB
                """, currentTime, os, javaVer, workDir, freeMemMb, totalMemMb);
    }
}
