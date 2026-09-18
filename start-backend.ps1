Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "正在检查并清理 8888 端口残留进程..." -ForegroundColor Yellow
$conn = Get-NetTCPConnection -LocalPort 8888 -ErrorAction SilentlyContinue
if ($conn) {
    $pids = $conn.OwningProcess | Select-Object -Unique
    foreach ($p in $pids) {
        Write-Host "发现占用端口 8888 的进程 PID: $p，正在强制终止..." -ForegroundColor Red
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}
Write-Host "8888 端口已就绪，正在启动 Spring Boot 后端服务..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Set-Location "$PSScriptRoot\backend"
mvn spring-boot:run