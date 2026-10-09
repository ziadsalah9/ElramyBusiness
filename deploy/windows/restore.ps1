#Requires -RunAsAdministrator
# Replaces ALL store data with a backup file (.dump), e.g. to move the data
# from the Mac or to recover. A safety backup of the current data is made
# first. Run restore.bat and pick the file.
param([string]$File)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\settings.ps1"

if (-not $File) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Filter = 'Store backup (*.dump)|*.dump'
    $dialog.Title = 'Choose the backup to restore'
    if ($dialog.ShowDialog() -ne 'OK') { Write-Host 'Cancelled.'; exit 1 }
    $File = $dialog.FileName
}
if (-not (Test-Path $File)) { throw "File not found: $File" }

Write-Host ''
Write-Host "This REPLACES ALL data in the store with:" -ForegroundColor Yellow
Write-Host "    $File" -ForegroundColor Yellow
if ((Read-Host 'Type YES to continue') -ne 'YES') { Write-Host 'Cancelled.'; exit 1 }

$pgBin = Find-PgBin
$env:PGPASSWORD = Read-Secret 'db.secret'
try {
    Write-Step 'Safety backup of the current data'
    try { & (Join-Path $PSScriptRoot 'backup.ps1') } catch { Write-Host "    (no safety backup: $($_.Exception.Message))" }

    Write-Step 'Stopping the store'
    Stop-ElramyApp

    Write-Step 'Recreating the database'
    $psql = Join-Path $pgBin 'psql.exe'
    & $psql -h localhost -p (Get-DbPort) -U $DbUser -d postgres -c "DROP DATABASE IF EXISTS `"$DbName`" WITH (FORCE)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not drop the old database.' }
    & $psql -h localhost -p (Get-DbPort) -U $DbUser -d postgres -c "CREATE DATABASE `"$DbName`" ENCODING 'UTF8' TEMPLATE template0" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the database.' }

    Write-Step 'Restoring'
    # --no-owner: everything belongs to the store's own user on this laptop.
    & (Join-Path $pgBin 'pg_restore.exe') -h localhost -p (Get-DbPort) -U $DbUser -d $DbName --no-owner --no-privileges $File
    if ($LASTEXITCODE -ne 0) { Write-Host '    pg_restore reported warnings (often harmless); check the store after it starts.' -ForegroundColor Yellow }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

# The restored data brings its own users, so the installer's first-login note no longer applies.
Remove-Item (Join-Path $ElramyRoot 'FIRST-LOGIN.txt') -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $SecretsDir 'admin-initial.secret') -Force -ErrorAction SilentlyContinue

Write-Step 'Starting the store'
Start-ScheduledTask -TaskName $AppTask
if (Wait-ElramyApp) { Write-Host "Done: http://localhost:$Port/" -ForegroundColor Green }
else { Write-Host "The store did not answer yet. Check $LogsDir\elramy.log" -ForegroundColor Red }
