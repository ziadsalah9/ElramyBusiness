#Requires -RunAsAdministrator
# Installs a new version: backup, swap elramy.jar (previous kept as
# elramy.previous.jar), start. Run update.bat from the new release folder.

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\settings.ps1"
$newJar = Join-Path $PSScriptRoot 'elramy.jar'
if (-not (Test-Path $newJar)) { throw "elramy.jar not found next to update.ps1" }

Write-Step 'Backup before updating'
& (Join-Path $AppDir 'backup.ps1')

Write-Step 'Stopping the store'
Stop-ElramyApp

Write-Step 'Installing the new version'
$current = Join-Path $AppDir 'elramy.jar'
if (Test-Path $current) { Copy-Item $current (Join-Path $AppDir 'elramy.previous.jar') -Force }
Copy-Item $newJar $current -Force
foreach ($f in @('settings.ps1', 'start-elramy.ps1', 'backup.ps1', 'restore.ps1', 'update.ps1', 'uninstall.ps1', 'VERSION.txt')) {
    $src = Join-Path $PSScriptRoot $f
    if ((Test-Path $src) -and ((Resolve-Path $src).Path -ne (Join-Path $AppDir $f))) { Copy-Item $src (Join-Path $AppDir $f) -Force }
}

Write-Step 'Starting the store'
Start-ScheduledTask -TaskName $AppTask
if (Wait-ElramyApp) {
    Write-Host "Updated: http://localhost:$Port/" -ForegroundColor Green
} else {
    Write-Host "The new version did not start. Check $LogsDir\elramy.log." -ForegroundColor Red
    Write-Host "To go back: copy $AppDir\elramy.previous.jar over elramy.jar and restart the laptop (restore the backup taken above if needed)."
}
