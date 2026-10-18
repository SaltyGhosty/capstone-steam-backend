# Preparación del PC como servidor de ARCADE. Se ejecuta UNA vez (repetirlo no rompe nada):
#   1. instala Caddy y cloudflared con winget
#   2. crea la base de datos de producción en el PostgreSQL local
#   3. crea el túnel de Cloudflare y apunta el dominio (PUBLIC_HOST) a este PC
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$cfg = Read-HostConfig
$secrets = Read-EnvFile (Join-Path $Root '.env')
New-Item -ItemType Directory -Force $LogDir | Out-Null

Write-Step 'Programas: Caddy y cloudflared'
foreach ($pkg in @(@{ Id = 'CaddyServer.Caddy'; Cmd = 'caddy' }, @{ Id = 'Cloudflare.cloudflared'; Cmd = 'cloudflared' })) {
    if (Get-Command $pkg.Cmd -ErrorAction SilentlyContinue) {
        Write-Host "$($pkg.Cmd) ya está instalado"
    } else {
        winget install --id $pkg.Id --exact --silent --accept-source-agreements --accept-package-agreements
        Update-Path
    }
}

Write-Step "Base de datos '$($cfg.DB_NAME)'"
$psql = Find-Psql
$dbUser = if ($secrets.DB_USER) { $secrets.DB_USER } else { 'postgres' }
$env:PGPASSWORD = $secrets.DB_PASSWORD
$exists = & $psql -h localhost -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$($cfg.DB_NAME)'"
if ($exists -eq '1') {
    Write-Host 'Ya existe'
} else {
    & $psql -h localhost -U $dbUser -d postgres -c "CREATE DATABASE $($cfg.DB_NAME)"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base de datos' }
    Write-Host 'Creada (las tablas las crea Flyway al arrancar el backend)'
}
Remove-Item Env:PGPASSWORD

Write-Step "Túnel de Cloudflare '$($cfg.TUNNEL_NAME)' -> https://$($cfg.PUBLIC_HOST)"
$cfDir = Join-Path $env:USERPROFILE '.cloudflared'
if (-not (Test-Path (Join-Path $cfDir 'cert.pem'))) {
    Write-Host 'Se abre el navegador: inicia sesión en Cloudflare y autoriza tu dominio.' -ForegroundColor Yellow
    cloudflared tunnel login
    if ($LASTEXITCODE -ne 0) { throw 'No se completó el login de Cloudflare' }
}
$tunnel = cloudflared tunnel list --output json | ConvertFrom-Json | Where-Object name -eq $cfg.TUNNEL_NAME
if (-not $tunnel) {
    cloudflared tunnel create $cfg.TUNNEL_NAME
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el túnel' }
    $tunnel = cloudflared tunnel list --output json | ConvertFrom-Json | Where-Object name -eq $cfg.TUNNEL_NAME
}
# Crea (o actualiza) el registro DNS del dominio para que apunte al túnel
cloudflared tunnel route dns --overwrite-dns $cfg.TUNNEL_NAME $cfg.PUBLIC_HOST
if ($LASTEXITCODE -ne 0) { throw "No se pudo apuntar $($cfg.PUBLIC_HOST) al túnel" }

$config = @"
# Generado por setup-host.ps1. Todo lo que llega a https://$($cfg.PUBLIC_HOST) va a Caddy en este PC.
tunnel: $($tunnel.id)
credentials-file: $(Join-Path $cfDir "$($tunnel.id).json")
ingress:
  - hostname: $($cfg.PUBLIC_HOST)
    service: http://localhost:$($cfg.WEB_PORT)
  - service: http_status:404
"@
Set-Content -Path (Join-Path $RunDir 'cloudflared.yml') -Value $config -Encoding UTF8

Write-Step 'Listo'
Write-Host 'Ahora arranca la tienda con:  deploy\local\start-arcade.cmd -Build -Seed'
