# Para ARCADE (túnel, web y backend) con los PIDs que guardó start-arcade.ps1.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

if (-not (Test-Path $PidFile)) {
    Write-Host 'ARCADE no está en marcha (no hay run\pids.json).'
    return
}
$pids = Get-Content $PidFile -Raw | ConvertFrom-Json
foreach ($name in 'cloudflared', 'caddy', 'backend') {
    $id = $pids.$name
    if (-not $id) { continue }
    $process = Get-Process -Id $id -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $id -Force -Confirm:$false
        Write-Host "Parado: $name (PID $id)"
    }
}
Remove-Item $PidFile
