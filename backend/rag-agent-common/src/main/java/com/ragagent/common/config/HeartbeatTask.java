package com.ragagent.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统健康存活心跳组件
 * 周期性输出健康状态，打破宿主控制台无IO空闲超时回收，保障系统平稳常驻
 */
@Slf4j
@Component
@EnableScheduling
public class HeartbeatTask {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Scheduled(fixedRate = 60000)
    public void reportHeartbeat() {
        log.info("💓 [System-Heartbeat] Java RAG + Agent 核心引擎与记忆中枢平稳运行中 - {}", LocalDateTime.now().format(FORMATTER));
    }
}
