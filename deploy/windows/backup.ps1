# Daily backup of the store database (scheduled task "Elramy Backup").
# Keeps $KeepLocalDays days in C:\Elramy\backups and copies every backup
# that is not there yet to a USB drive named $UsbLabel when it is plugged in.
# Run it by hand any time:  powershell -ExecutionPolicy Bypass -File backup.ps1

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\settings.ps1"
$log = Join-Path $LogsDir 'backup.log'
function Log([string]$text) { Add-Content -Path $log -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $text" -Encoding UTF8 }

try {
    $pgBin = Find-PgBin
    if (-not $pgBin) { throw 'PostgreSQL tools not found.' }
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

    $env:PGPASSWORD = Read-Secret 'db.secret'
    $file = Join-Path $BackupDir ("ElramyGroup-{0}.dump" -f (Get-Date -Format 'yyyy-MM-dd_HHmm'))
    $tmp = "$file.partial"
    & (Join-Path $pgBin 'pg_dump.exe') -h localhost -p (Get-DbPort) -U $DbUser -Fc -f $tmp $DbName
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)" }
    if ((Get-Item $tmp).Length -lt 1024) { Remove-Item $tmp -Force; throw 'Backup file too small, discarded.' }
    Move-Item $tmp $file -Force
    Log "backup ok: $file"

    Get-ChildItem $BackupDir -Filter 'ElramyGroup-*.dump' |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepLocalDays) } |
        Remove-Item -Force

    $usb = Get-Volume -ErrorAction SilentlyContinue |
        Where-Object { $_.FileSystemLabel -eq $UsbLabel -and $_.DriveLetter } | Select-Object -First 1
    if ($usb) {
        $dest = "$($usb.DriveLetter):\ElramyBackups"
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        $copied = 0
        foreach ($b in Get-ChildItem $BackupDir -Filter 'ElramyGroup-*.dump') {
            $target = Join-Path $dest $b.Name
            if (-not (Test-Path $target)) { Copy-Item $b.FullName $target; $copied++ }
        }
        Get-ChildItem $dest -Filter 'ElramyGroup-*.dump' |
            Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepUsbDays) } |
            Remove-Item -Force
        Log "USB $($usb.DriveLetter): copied $copied file(s)"
    } else {
        Log "USB drive '$UsbLabel' not connected - kept local only"
    }
} catch {
    Log "BACKUP FAILED: $($_.Exception.Message)"
    throw
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
