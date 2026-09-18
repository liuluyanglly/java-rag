Write-Host "正在停止 8888 端口后端服务..." -ForegroundColor Yellow
$conn = Get-NetTCPConnection -LocalPort 8888 -ErrorAction SilentlyContinue
if ($conn) {
    $pids = $conn.OwningProcess | Select-Object -Unique
    foreach ($p in $pids) {
        Write-Host "已终止进程 PID: $p" -ForegroundColor Green
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Write-Host "8888 端口已彻底释放！" -ForegroundColor Green
} else {
    Write-Host "当前 8888 端口未被占用。" -ForegroundColor Cyan
}