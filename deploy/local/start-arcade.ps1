# Arranca ARCADE alojado en este PC: backend (Spring Boot), web (Caddy) y túnel (cloudflared).
#   -Build  compila de nuevo backend y frontend (obligatorio la primera vez y tras cambiar código)
#   -Seed   carga el catálogo demo en la base de datos de producción (se puede repetir)
#   -NoTunnel  sin cloudflared: solo este PC y tu red local (http://IP-del-PC:8088)
param([switch]$Build, [switch]$Seed, [switch]$NoTunnel)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$cfg = Read-HostConfig -AllowNoDomain:$NoTunnel
$secrets = Read-EnvFile (Join-Path $Root '.env')
$publicUrl = if ($NoTunnel) { "http://localhost:$($cfg.WEB_PORT)" } else { "https://$($cfg.PUBLIC_HOST)" }
$jar = Join-Path $RunDir 'arcade.jar'
$web = Join-Path $RunDir 'web'
$tunnelConfig = Join-Path $RunDir 'cloudflared.yml'
New-Item -ItemType Directory -Force $LogDir | Out-Null
Update-Path

if (Test-Path $PidFile) {
    throw 'ARCADE ya parece estar en marcha. Páralo antes con deploy\local\stop-arcade.cmd'
}
if (-not $NoTunnel -and -not (Test-Path $tunnelConfig)) {
    throw 'Falta la configuración del túnel: ejecuta antes deploy\local\setup-host.cmd'
}

if ($Build -or -not (Test-Path $jar) -or -not (Test-Path $web)) {
    Write-Step 'Compilando el backend'
    Push-Location $Root
    try {
        & .\mvnw.cmd -q -B package
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación del backend' }
    } finally { Pop-Location }
    $built = Get-ChildItem (Join-Path $Root 'target\*.jar') | Where-Object Name -notlike '*plain*' | Select-Object -First 1
    Copy-Item $built.FullName $jar -Force

    Write-Step 'Compilando el frontend'
    Push-Location (Join-Path $Root 'frontend')
    try {
        if (-not (Test-Path node_modules)) { npm ci; if ($LASTEXITCODE -ne 0) { throw 'Falló npm ci' } }
        $env:VITE_API_URL = '' # la API va por el mismo origen (Caddy la reenvía)
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación del frontend' }
    } finally { Pop-Location }
    # Copia propia: un "npm run build" de desarrollo no cambia la web publicada
    if (Test-Path $web) { Remove-Item $web -Recurse -Force }
    Copy-Item (Join-Path $Root 'frontend\dist') $web -Recurse
}

Write-Step "Backend en el puerto $($cfg.BACKEND_PORT)"
# Los secretos salen del .env de la raíz; aquí solo se cambia lo propio de producción
$dbUser = if ($secrets.DB_USER) { $secrets.DB_USER } else { 'postgres' }
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = $cfg.BACKEND_PORT
$env:DB_URL = "jdbc:postgresql://localhost:5432/$($cfg.DB_NAME)"
$env:DB_USER = $dbUser
$env:DB_PASSWORD = $secrets.DB_PASSWORD
$env:CORS_ALLOWED_ORIGINS = $publicUrl
$env:FRONTEND_URL = $publicUrl
$backend = Start-Process java -ArgumentList '-XX:MaxRAMPercentage=50', '-jar', "`"$jar`"" -WorkingDirectory $Root `
    -RedirectStandardOutput (Join-Path $LogDir 'backend.log') -RedirectStandardError (Join-Path $LogDir 'backend.err.log') `
    -WindowStyle Hidden -PassThru
$pids = @{ backend = $backend.Id }
$pids | ConvertTo-Json | Set-Content $PidFile

$healthUrl = "http://localhost:$($cfg.BACKEND_PORT)/actuator/health"
$up = $false
for ($i = 0; $i -lt 90 -and -not $backend.HasExited; $i++) {
    try { if ((Invoke-RestMethod $healthUrl -TimeoutSec 2).status -eq 'UP') { $up = $true; break } } catch { Start-Sleep -Seconds 1 }
}
if (-not $up) {
    & (Join-Path $PSScriptRoot 'stop-arcade.ps1')
    throw "El backend no arrancó. Mira deploy\local\run\logs\backend.log"
}
Write-Host 'Backend listo'

Write-Step "Web (Caddy) en el puerto $($cfg.WEB_PORT)"
$env:WEB_PORT = $cfg.WEB_PORT
$env:BACKEND_PORT = $cfg.BACKEND_PORT
$env:WEB_ROOT = $web
$caddy = Start-Process caddy -ArgumentList 'run', '--config', "`"$(Join-Path $PSScriptRoot 'Caddyfile')`"", '--adapter', 'caddyfile' `
    -RedirectStandardOutput (Join-Path $LogDir 'caddy.log') -RedirectStandardError (Join-Path $LogDir 'caddy.err.log') `
    -WindowStyle Hidden -PassThru
$pids.caddy = $caddy.Id
$pids | ConvertTo-Json | Set-Content $PidFile
$webUp = $false
for ($i = 0; $i -lt 15 -and -not $caddy.HasExited; $i++) {
    try { Invoke-WebRequest "http://localhost:$($cfg.WEB_PORT)/" -UseBasicParsing -TimeoutSec 2 | Out-Null; $webUp = $true; break } catch { Start-Sleep -Seconds 1 }
}
if (-not $webUp) {
    & (Join-Path $PSScriptRoot 'stop-arcade.ps1')
    throw "Caddy no arrancó. Mira deploy\local\run\logs\caddy.err.log"
}
Write-Host 'Web lista'

if (-not $NoTunnel) {
    Write-Step "Túnel de Cloudflare -> $publicUrl"
    $tunnel = Start-Process cloudflared -ArgumentList 'tunnel', '--config', "`"$tunnelConfig`"", 'run' `
        -RedirectStandardOutput (Join-Path $LogDir 'cloudflared.log') -RedirectStandardError (Join-Path $LogDir 'cloudflared.err.log') `
        -WindowStyle Hidden -PassThru
    $pids.cloudflared = $tunnel.Id
    $pids | ConvertTo-Json | Set-Content $PidFile
}

if ($Seed) {
    Write-Step 'Catálogo demo'
    node (Join-Path $Root 'scripts\seed-demo.mjs') --api "http://localhost:$($cfg.BACKEND_PORT)"
}

Write-Step 'ARCADE está en marcha'
Write-Host "Público:  $publicUrl"
Write-Host "Local:    http://localhost:$($cfg.WEB_PORT)"
Write-Host 'Logs:     deploy\local\run\logs'
Write-Host 'Parar:    deploy\local\stop-arcade.cmd'
