#Requires -RunAsAdministrator
# One-time setup of the Elramy store on this Windows laptop.
# Safe to run again (e.g. after changing settings): nothing is deleted.

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\settings.ps1"

Write-Step 'Checking Java (17 or newer)'
$java = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $java) {
    throw 'Java was not found. Install "Eclipse Temurin 21" from https://adoptium.net (tick "Add to PATH"), then run install.bat again.'
}
$javaPath = $java.Source
$versionLine = (& cmd /c "`"$javaPath`" -version 2>&1" | Select-Object -First 1)
if ($versionLine -match '"(\d+)') {
    if ([int]$Matches[1] -lt 17) { throw "Java $($Matches[1]) is too old; install Java 21 from https://adoptium.net" }
}
Write-Host "    $versionLine"

Write-Step 'Checking PostgreSQL'
$pgBin = Find-PgBin
if (-not $pgBin) {
    throw 'PostgreSQL was not found. Install PostgreSQL 16 from https://www.postgresql.org/download/windows/ then run install.bat again.'
}
Write-Host "    $pgBin"

Write-Step "Creating folders under $ElramyRoot"
foreach ($d in @($ElramyRoot, $AppDir, $SecretsDir, $LogsDir, $BackupDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}
# Only administrators and Windows itself may read the secrets.
& icacls $SecretsDir /inheritance:r /grant:r 'Administrators:(OI)(CI)F' 'SYSTEM:(OI)(CI)F' | Out-Null

Write-Step 'Copying the application'
Copy-Item (Join-Path $PSScriptRoot 'elramy.jar') (Join-Path $AppDir 'elramy.jar') -Force
foreach ($f in @('settings.ps1', 'start-elramy.ps1', 'backup.ps1', 'restore.ps1', 'update.ps1', 'uninstall.ps1', 'VERSION.txt')) {
    $src = Join-Path $PSScriptRoot $f
    if (Test-Path $src) { Copy-Item $src (Join-Path $AppDir $f) -Force }
}
Set-Content -Path (Join-Path $AppDir 'java-path.txt') -Value $javaPath -Encoding ASCII

Write-Step 'Secrets'
$jwtFile = Join-Path $SecretsDir 'jwt.secret'
if (-not (Test-Path $jwtFile)) {
    Set-Content -Path $jwtFile -Value (New-RandomSecret 48) -Encoding ASCII -NoNewline
    Write-Host '    New login-signing key created.'
}

$dbFile = Join-Path $SecretsDir 'db.secret'
if (-not (Test-Path $dbFile)) {
    Write-Host ''
    Write-Host 'Enter the password of the PostgreSQL "postgres" user (chosen when PostgreSQL was installed).'
    Write-Host 'It is used once, to create the store database and its own user; it is not saved.'
    $secure = Read-Host -AsSecureString 'postgres password'
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    $psql = Join-Path $pgBin 'psql.exe'

    $dbPassword = New-RandomSecret 24
    $roleExists = & $psql -h localhost -p (Get-DbPort) -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$DbUser'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to PostgreSQL as "postgres". Check the password and that the PostgreSQL service is running.' }
    if ($roleExists -match '1') {
        & $psql -h localhost -p (Get-DbPort) -U postgres -d postgres -c "ALTER ROLE $DbUser WITH LOGIN CREATEDB PASSWORD '$dbPassword'" | Out-Null
    } else {
        & $psql -h localhost -p (Get-DbPort) -U postgres -d postgres -c "CREATE ROLE $DbUser WITH LOGIN CREATEDB PASSWORD '$dbPassword'" | Out-Null
    }
    $dbExists = & $psql -h localhost -p (Get-DbPort) -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
    if (-not ($dbExists -match '1')) {
        Invoke-PgSql $pgBin 'postgres' 'postgres' "CREATE DATABASE `"$DbName`" OWNER $DbUser ENCODING 'UTF8' TEMPLATE template0;" | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { throw 'Creating the store database failed.' }
    Remove-Item Env:\PGPASSWORD
    Set-Content -Path $dbFile -Value $dbPassword -Encoding ASCII -NoNewline
    Write-Host "    Database $DbName and user $DbUser are ready."

    # First admin of an empty database: shown once, below.
    $adminFile = Join-Path $SecretsDir 'admin-initial.secret'
    if (-not (Test-Path $adminFile)) {
        Set-Content -Path $adminFile -Value (New-RandomSecret 9) -Encoding ASCII -NoNewline
    }
}

Write-Step 'Starting automatically with Windows'
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$appAction = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$AppDir\start-elramy.ps1`""
$appSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName $AppTask -Action $appAction -Trigger (New-ScheduledTaskTrigger -AtStartup) `
    -Principal $principal -Settings $appSettings -Description 'Elramy store application' -Force | Out-Null

$backupAction = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$AppDir\backup.ps1`""
$backupSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)
Register-ScheduledTask -TaskName $BackupTask -Action $backupAction -Trigger (New-ScheduledTaskTrigger -Daily -At $BackupTime) `
    -Principal $principal -Settings $backupSettings -Description 'Elramy daily database backup (local + USB)' -Force | Out-Null
Write-Host "    '$AppTask' starts at boot; '$BackupTask' runs daily at $BackupTime."

Write-Step 'Desktop shortcut'
$shortcut = 'C:\Users\Public\Desktop\Elramy.url'
Set-Content -Path $shortcut -Value "[InternetShortcut]`r`nURL=http://localhost:$Port/" -Encoding ASCII

Write-Step 'Starting the store (first start can take a minute)'
Stop-ElramyApp
Start-ScheduledTask -TaskName $AppTask
if (Wait-ElramyApp) {
    Write-Host ''
    Write-Host "Ready: http://localhost:$Port/  (shortcut 'Elramy' on the desktop)" -ForegroundColor Green
    $adminFile = Join-Path $SecretsDir 'admin-initial.secret'
    if (Test-Path $adminFile) {
        Write-Host ''
        Write-Host 'If this is a NEW, empty database, log in with:' -ForegroundColor Yellow
        Write-Host "    user: admin1    password: $(Get-Content $adminFile -Raw)" -ForegroundColor Yellow
        Write-Host '    Change it right away (Users page). If you restore your data from the Mac, use your usual login instead.'
    }
    Start-Process "http://localhost:$Port/"
} else {
    Write-Host "The store did not answer yet. Check $LogsDir\elramy.log" -ForegroundColor Red
}
