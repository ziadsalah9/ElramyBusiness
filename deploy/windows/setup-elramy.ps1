#Requires -RunAsAdministrator
# Run by Elramy-Setup.exe after it copied the files (app, bundled Java and
# PostgreSQL) to C:\Elramy. Safe to run again (upgrade/repair): nothing is
# deleted, an existing database and its secrets are reused.
# Log: C:\Elramy\logs\setup.log

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\settings.ps1"
New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null
Start-Transcript -Path (Join-Path $LogsDir 'setup.log') -Append | Out-Null

try {
    Write-Step 'Folders and permissions'
    foreach ($d in @($ElramyRoot, $AppDir, $SecretsDir, $LogsDir, $BackupDir)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
    & icacls $SecretsDir /inheritance:r /grant:r 'Administrators:(OI)(CI)F' 'SYSTEM:(OI)(CI)F' | Out-Null

    if (-not (Test-Path $BundledJava)) { throw "Bundled Java missing: $BundledJava" }
    Set-Content -Path (Join-Path $AppDir 'java-path.txt') -Value $BundledJava -Encoding ASCII

    $jwtFile = Join-Path $SecretsDir 'jwt.secret'
    if (-not (Test-Path $jwtFile)) { Set-Content -Path $jwtFile -Value (New-RandomSecret 48) -Encoding ASCII -NoNewline }

    # ------------------------------------------------------------ PostgreSQL (private to the store)
    $pgBin = $BundledPgBin
    $superFile = Join-Path $SecretsDir 'pg-super.secret'
    $portFile = Join-Path $SecretsDir 'pg.port'
    $newCluster = -not (Test-Path (Join-Path $PgDataDir 'PG_VERSION'))

    if ($newCluster) {
        Write-Step 'Creating the store database server'
        # 5432 is the usual port; another PostgreSQL may already use it.
        $dbPort = 5432
        while (Get-NetTCPConnection -LocalPort $dbPort -State Listen -ErrorAction SilentlyContinue) { $dbPort++ }
        Set-Content -Path $portFile -Value $dbPort -Encoding ASCII -NoNewline
        if (-not (Test-Path $superFile)) { Set-Content -Path $superFile -Value (New-RandomSecret 24) -Encoding ASCII -NoNewline }

        # A previous attempt may have left a half-made folder: keep it aside, initdb needs an empty one.
        if ((Test-Path $PgDataDir) -and (Get-ChildItem $PgDataDir -Force | Select-Object -First 1)) {
            Rename-Item $PgDataDir ("pgdata.failed-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
        }
        New-Item -ItemType Directory -Force -Path $PgDataDir | Out-Null
        # initdb drops its administrator rights (restricted token) before it works, so access
        # given only to Administrators is not enough: give this user the data folder and the
        # password file (the secrets folder itself stays Administrators/SYSTEM only).
        $me = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        & icacls $PgDataDir /grant "*${me}:(OI)(CI)F" | Out-Null
        $pwFile = Join-Path $SecretsDir 'initdb.tmp'
        Set-Content -Path $pwFile -Value (Read-Secret 'pg-super.secret') -Encoding ASCII -NoNewline
        & icacls $pwFile /grant "*${me}:R" | Out-Null
        & (Join-Path $pgBin 'initdb.exe') -D $PgDataDir -U postgres --pwfile=$pwFile -E UTF8 --locale=C --auth=scram-sha-256
        $code = $LASTEXITCODE
        Remove-Item $pwFile -Force
        if ($code -eq -1073741515) { throw 'initdb could not start: a DLL is missing (install the Microsoft Visual C++ 2015-2022 x64 runtime).' }
        if ($code -ne 0) { throw "initdb failed (exit $code)" }
        # Only this laptop connects.
        Add-Content -Path (Join-Path $PgDataDir 'postgresql.conf') -Value "`r`nlisten_addresses = 'localhost'`r`nport = $dbPort`r`n"
    }
    $dbPort = Get-DbPort

    # The service runs as Network Service, which needs access to the data.
    & icacls $PgDataDir /grant 'NT AUTHORITY\NetworkService:(OI)(CI)F' /T /Q | Out-Null
    if (-not (Get-Service -Name $PgServiceName -ErrorAction SilentlyContinue)) {
        Write-Step 'Registering the database service'
        & (Join-Path $pgBin 'pg_ctl.exe') register -N $PgServiceName -U 'NT AUTHORITY\NetworkService' -D $PgDataDir -S auto -w
        if ($LASTEXITCODE -ne 0) { throw 'Could not register the PostgreSQL service.' }
    }
    Set-Service -Name $PgServiceName -StartupType Automatic
    if ((Get-Service -Name $PgServiceName).Status -ne 'Running') { Start-Service -Name $PgServiceName }

    # Wait until it accepts connections.
    $deadline = (Get-Date).AddSeconds(60)
    do {
        & (Join-Path $pgBin 'pg_isready.exe') -h localhost -p $dbPort | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL did not start on port $dbPort." }

    # ------------------------------------------------------------ the store database and its own user
    $dbFile = Join-Path $SecretsDir 'db.secret'
    $psql = Join-Path $pgBin 'psql.exe'
    $env:PGPASSWORD = Read-Secret 'pg-super.secret'
    if (-not (Test-Path $dbFile)) { Set-Content -Path $dbFile -Value (New-RandomSecret 24) -Encoding ASCII -NoNewline }
    $dbPassword = Read-Secret 'db.secret'
    $roleExists = & $psql -h localhost -p $dbPort -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$DbUser'"
    if ($roleExists -match '1') {
        & $psql -h localhost -p $dbPort -U postgres -d postgres -c "ALTER ROLE $DbUser WITH LOGIN CREATEDB PASSWORD '$dbPassword'" | Out-Null
    } else {
        & $psql -h localhost -p $dbPort -U postgres -d postgres -c "CREATE ROLE $DbUser WITH LOGIN CREATEDB PASSWORD '$dbPassword'" | Out-Null
    }
    $dbExists = & $psql -h localhost -p $dbPort -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
    $newDatabase = -not ($dbExists -match '1')
    if ($newDatabase) {
        & $psql -h localhost -p $dbPort -U postgres -d postgres -c "CREATE DATABASE `"$DbName`" OWNER $DbUser ENCODING 'UTF8' TEMPLATE template0" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Creating the store database failed.' }
        Set-Content -Path (Join-Path $SecretsDir 'admin-initial.secret') -Value (New-RandomSecret 9) -Encoding ASCII -NoNewline
    }
    Remove-Item Env:\PGPASSWORD

    # ------------------------------------------------------------ start with Windows + daily backup
    Write-Step 'Scheduled tasks'
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    $appAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$AppDir\start-elramy.ps1`""
    $appSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
    Register-ScheduledTask -TaskName $AppTask -Action $appAction -Trigger (New-ScheduledTaskTrigger -AtStartup) `
        -Principal $principal -Settings $appSettings -Description 'Elramy store application' -Force | Out-Null
    $backupAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$AppDir\backup.ps1`""
    $backupSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 1)
    Register-ScheduledTask -TaskName $BackupTask -Action $backupAction -Trigger (New-ScheduledTaskTrigger -Daily -At $BackupTime) `
        -Principal $principal -Settings $backupSettings -Description 'Elramy daily database backup (local + USB)' -Force | Out-Null

    Write-Step 'Starting the store'
    Stop-ElramyApp
    Start-ScheduledTask -TaskName $AppTask
    $ready = Wait-ElramyApp 240

    $note = Join-Path $ElramyRoot 'FIRST-LOGIN.txt'
    $adminFile = Join-Path $SecretsDir 'admin-initial.secret'
    if ($newDatabase) {
        @(
            'مفروشات الرامي - أول دخول / First login',
            '',
            "  http://localhost:$Port/",
            '  user / المستخدم:      admin1',
            "  password / كلمة المرور: $(Get-Content $adminFile -Raw)",
            '',
            'غيّر كلمة المرور فوراً من صفحة المستخدمين. لنقل بياناتك من جهاز آخر استخدم "استرجاع نسخة احتياطية" من قائمة ابدأ.',
            'Change the password right away (Users page). To bring your data from another computer use "Restore backup" in the Start menu.'
        ) | Set-Content -Path $note -Encoding UTF8
    } elseif (Test-Path $note) {
        Remove-Item $note -Force
    }
    if (-not $ready) { throw "The store did not answer on http://localhost:$Port/ - see $LogsDir\elramy.log" }
    Write-Host "Ready: http://localhost:$Port/" -ForegroundColor Green
} catch {
    Write-Host "SETUP FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Stop-Transcript | Out-Null
    exit 1
}
Stop-Transcript | Out-Null
exit 0
