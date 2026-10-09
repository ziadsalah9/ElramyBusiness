# Shared settings for the Elramy store scripts (Windows PowerShell 5.1+).
# Change values here before running install.bat if needed.

$ElramyRoot     = 'C:\Elramy'
$AppDir         = Join-Path $ElramyRoot 'app'
$SecretsDir     = Join-Path $ElramyRoot 'secrets'
$LogsDir        = Join-Path $ElramyRoot 'logs'
$BackupDir      = Join-Path $ElramyRoot 'backups'

$Port           = 8090
$DbName         = 'ElramyGroup'
# The Setup.exe installer bundles Java and PostgreSQL here; the zip kit uses
# the system ones instead.
$BundledJava    = Join-Path $ElramyRoot 'jre\bin\java.exe'
$BundledPgBin   = Join-Path $ElramyRoot 'pgsql\bin'
$PgDataDir      = Join-Path $ElramyRoot 'pgdata'
$PgServiceName  = 'ElramyPostgreSQL'
$DbUser         = 'elramy'

# Backups are also copied to any USB drive whose name (volume label) is this.
$UsbLabel       = 'ELRAMY-BACKUP'
$KeepLocalDays  = 30
$KeepUsbDays    = 90

$AppTask        = 'Elramy Store'
$BackupTask     = 'Elramy Backup'
$BackupTime     = '23:30'

function Write-Step([string]$text) { Write-Host "==> $text" -ForegroundColor Cyan }

function Get-DbPort {
    # The bundled PostgreSQL may run on another port if 5432 was taken.
    $file = Join-Path $SecretsDir 'pg.port'
    if (Test-Path $file) { return [int]((Get-Content $file -Raw).Trim()) }
    return 5432
}

function Find-PgBin {
    if (Test-Path (Join-Path $BundledPgBin 'pg_dump.exe')) { return $BundledPgBin }
    # Newest PostgreSQL installed by the official Windows installer.
    $dirs = Get-ChildItem 'C:\Program Files\PostgreSQL' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\pg_dump.exe') } |
        Sort-Object { [int]($_.Name -replace '\D', '') } -Descending
    if ($dirs) { return (Join-Path $dirs[0].FullName 'bin') }
    $cmd = Get-Command pg_dump.exe -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path $cmd.Source) }
    return $null
}

function Invoke-PgSql([string]$PgBin, [string]$User, [string]$Database, [string]$Sql) {
    # The SQL goes through a file, not -c: Windows PowerShell 5.1 drops double quotes
    # inside arguments to programs, so CREATE DATABASE "ElramyGroup" would reach psql
    # as CREATE DATABASE ElramyGroup and create "elramygroup" instead.
    $file = Join-Path $env:TEMP ('elramy-' + [guid]::NewGuid().ToString() + '.sql')
    Set-Content -Path $file -Value $Sql -Encoding ASCII
    try {
        & (Join-Path $PgBin 'psql.exe') -X -q -v ON_ERROR_STOP=1 -h localhost -p (Get-DbPort) -U $User -d $Database -f $file
    } finally {
        Remove-Item $file -Force -ErrorAction SilentlyContinue
    }
}

function Read-Secret([string]$name) {
    $file = Join-Path $SecretsDir $name
    if (-not (Test-Path $file)) { throw "Missing secret file $file - run install.bat first." }
    return (Get-Content $file -Raw).Trim()
}

function New-RandomSecret([int]$bytes = 32) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    # Letters and digits only: safe in SQL, command lines and config.
    return (([Convert]::ToBase64String($buffer)) -replace '[^A-Za-z0-9]', '')
}

function Stop-ElramyApp {
    Stop-ScheduledTask -TaskName $AppTask -ErrorAction SilentlyContinue
    # The task runs java through cmd; stop whatever still listens on the port.
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

function Wait-ElramyApp([int]$seconds = 180) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}
