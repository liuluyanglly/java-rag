package com.ragagent;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

import java.net.InetAddress;

@Slf4j
@SpringBootApplication
@EnableAsync
@MapperScan("com.ragagent.**.mapper")
public class RagAgentApplication {

    private static final java.util.concurrent.CountDownLatch LATCH = new java.util.concurrent.CountDownLatch(1);

    public static void main(String[] args) {
        // 0. 启动前自愈守护：强制清理目标端口(默认 8888)历史残留进程，彻底杜绝 PortInUseException 冲突
        int targetPort = resolvePort(args);
        killPortIfOccupied(targetPort);

        // 1. 注册全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("💥 [FATAL] 线程发生未捕获致命异常: thread={}, msg={}", thread.getName(), throwable.getMessage(), throwable);
        });

        // 2. 注册 JVM 关闭钩子，精准记录触发退出的调用栈
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warn("⚠️ [SHUTDOWN] JVM 收到关闭信号，准备退出！");
        }, "ShutdownHook-Diagnostics"));

        ConfigurableApplicationContext application = SpringApplication.run(RagAgentApplication.class, args);
        Environment env = application.getEnvironment();
        String ip = "127.0.0.1";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }
        String port = env.getProperty("server.port", "8888");
        String path = env.getProperty("server.servlet.context-path", "");
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        log.info("\n----------------------------------------------------------\n\t" +
                "🎉 Java RAG + Agent 平台 (Spring AI Alibaba 2.0) 启动成功!\n\t" +
                "🌐 后端本地接口: \thttp://localhost:" + port + "/" + path + "\n\t" +
                "📖 在线API接口文档 (Scalar): \thttp://localhost:" + port + "/" + path + "scalar (或 /docs)\n\t" +
                "📄 OpenAPI 3 契约元数据: \thttp://localhost:" + port + "/" + path + "v3/api-docs\n\t" +
                "💻 前端用户工作台: \thttp://localhost:6666/chat\n\t" +
                "⚙️ 前端管理控制台: \thttp://localhost:6666/admin/dataset\n" +
                "----------------------------------------------------------");

        // 3. 使用 CountDownLatch 保持非守护主线程永久存活
        try {
            LATCH.await();
        } catch (InterruptedException e) {
            log.warn("主线程被中断");
            Thread.currentThread().interrupt();
        }
    }

    private static int resolvePort(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--server.port=")) {
                    try {
                        return Integer.parseInt(arg.substring("--server.port=".length()).trim());
                    } catch (Exception ignored) {}
                }
            }
        }
        return 8888;
    }

    /**
     * 在 Spring Boot 启动前自动检查并强制清理目标端口历史占用进程 (跨平台自愈守卫)
     */
    private static void killPortIfOccupied(int port) {
        try {
            long currentPid = ProcessHandle.current().pid();
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Windows 环境下优先使用 PowerShell 精准清理非当前进程的 Listen 端口占用
                String psCmd = String.format(
                        "Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue " +
                        "| Where-Object { $_.OwningProcess -ne %d } " +
                        "| ForEach-Object { Write-Output $_.OwningProcess; Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }",
                        port, currentPid);

                Process ps = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCmd).start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(ps.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            System.out.println("🛡️ [Port-Guard] 启动自愈：检测到端口 " + port + " 被进程 PID=" + line + " 占用，已自动强制终结并释放！");
                        }
                    }
                }
                ps.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                // 兜底：采用 netstat + taskkill 双保险清理
                String cmdFallback = String.format(
                        "for /f \"tokens=5\" %%a in ('netstat -ano ^| findstr \":%d\" ^| findstr \"LISTENING\"') do if not \"%%a\"==\"%d\" taskkill /F /PID %%a",
                        port, currentPid);
                new ProcessBuilder("cmd.exe", "/c", cmdFallback).start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS);

                // 预留系统内核释放 socket 资源的短暂时间
                Thread.sleep(800);
            } else {
                // Linux / macOS 兜底清理
                String cmd = String.format("lsof -ti:%d | grep -v %d | xargs -r kill -9", port, currentPid);
                new ProcessBuilder("sh", "-c", cmd).start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                Thread.sleep(400);
            }
        } catch (Exception e) {
            System.err.println("⚠️ [Port-Guard] 自动清理端口 " + port + " 时捕获异常: " + e.getMessage());
        }
    }
}
