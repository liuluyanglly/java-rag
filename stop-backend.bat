@echo off
chcp 65001 >nul
echo 正在停止占用 8888 端口的后端 Java 进程...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8888" ^| findstr "LISTENING"') do (
    echo 终止进程 PID=%%a
    taskkill /F /PID %%a
)
echo 8888 端口已完全释放，您现在可以在 IntelliJ IDEA 中点击 Run 启动！
pause
