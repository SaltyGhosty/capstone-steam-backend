# Utilidades compartidas por los scripts de alojamiento local (se cargan con ". common.ps1").

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$RunDir = Join-Path $PSScriptRoot 'run'
$LogDir = Join-Path $RunDir 'logs'
$PidFile = Join-Path $RunDir 'pids.json'

function Read-EnvFile([string]$Path) {
    # Lee un fichero KEY=valor (ignora comentarios y líneas vacías) y devuelve un hashtable.
    $values = @{}
    if (-not (Test-Path $Path)) { return $values }
    foreach ($line in Get-Content $Path -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $values[$Matches[1]] = $Matches[2].Trim().Trim('"')
        }
    }
    return $values
}

function Read-HostConfig([switch]$AllowNoDomain) {
    $path = Join-Path $PSScriptRoot 'host.env'
    if (-not (Test-Path $path)) {
        throw "Falta deploy\local\host.env: copia host.env.example como host.env y pon tu dominio en PUBLIC_HOST."
    }
    $cfg = Read-EnvFile $path
    if (-not $AllowNoDomain -and (-not $cfg.PUBLIC_HOST -or $cfg.PUBLIC_HOST -like '*tudominio*')) {
        throw "Pon tu dominio real en PUBLIC_HOST (deploy\local\host.env)."
    }
    foreach ($default in @{ TUNNEL_NAME = 'arcade'; DB_NAME = 'arcade'; BACKEND_PORT = '8090'; WEB_PORT = '8088' }.GetEnumerator()) {
        if (-not $cfg[$default.Key]) { $cfg[$default.Key] = $default.Value }
    }
    return $cfg
}

function Update-Path {
    # winget añade los programas al PATH del sistema; la sesión actual no lo ve hasta recargarlo.
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}

function Find-Psql {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $psql) { throw 'No encuentro psql.exe en C:\Program Files\PostgreSQL. ¿Está instalado PostgreSQL?' }
    return $psql.FullName
}

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "==> $Text" -ForegroundColor Green
}
