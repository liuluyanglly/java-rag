@echo off
chcp 65001 >nul
title Java RAG + Agent 后端服务
echo ============================================================
echo   Java RAG + Agent 核心引擎独立启动器 (自动清障保活版)
echo ============================================================

set "JAVA_HOME=C:\Users\NB22069\.jdks\ms-21.0.12.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/3] 检查并清理 8888 端口历史残留进程...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8888 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Write-Host '正在终止占用 8888 端口的进程 PID:' $_.OwningProcess -ForegroundColor Yellow; Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8888" ^| findstr "LISTENING"') do (
    echo 兜底清理 PID=%%a ...
    taskkill /F /PID %%a >nul 2>&1
)

timeout /t 1 /nobreak >nul

echo [2/3] 验证 JDK 21 环境...
"%JAVA_HOME%\bin\java.exe" -version

echo [3/3] 正在启动后端 Spring Boot 4.0.0 (Netty WebFlux)...
cd /d "%~dp0"

set "JAR_PATH=backend\rag-agent-server\target\rag-agent-server-1.0.0.jar"
if not exist "%JAR_PATH%" (
    set "JAR_PATH=backend\target\java-rag-agent-backend-1.0.0.jar"
)

"%JAVA_HOME%\bin\java.exe" ^
  -jar "%JAR_PATH%" ^
  --spring.profiles.active=dev

pause
