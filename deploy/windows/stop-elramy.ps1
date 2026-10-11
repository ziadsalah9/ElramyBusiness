# Used by Elramy-Setup.exe before it replaces files (upgrade) and by the
# uninstaller: back up the data (upgrade), stop the app and the database.
# Does nothing on a computer where the store is not installed yet.
param([switch]$Backup, [switch]$Unregister)

$settings = 'C:\Elramy\app\settings.ps1'
if (-not (Test-Path $settings)) { exit 0 }
. $settings

if ($Backup -and (Test-Path (Join-Path $AppDir 'backup.ps1')) -and (Test-Path (Join-Path $SecretsDir 'db.secret'))) {
    try { & (Join-Path $AppDir 'backup.ps1') } catch { Write-Host "backup before upgrade failed: $($_.Exception.Message)" }
}

Stop-ElramyApp
# Any java still running from the bundled runtime.
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$ElramyRoot\*" } | Stop-Process -Force -ErrorAction SilentlyContinue

if (Get-Service -Name $PgServiceName -ErrorAction SilentlyContinue) {
    Stop-Service -Name $PgServiceName -Force -ErrorAction SilentlyContinue
    if ($Unregister) {
        & (Join-Path $BundledPgBin 'pg_ctl.exe') unregister -N $PgServiceName
    }
}

if ($Unregister) {
    Unregister-ScheduledTask -TaskName $AppTask -Confirm:$false -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $BackupTask -Confirm:$false -ErrorAction SilentlyContinue
}
exit 0
