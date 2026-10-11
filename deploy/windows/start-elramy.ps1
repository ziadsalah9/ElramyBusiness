# Runs the store. Started by the "Elramy Store" scheduled task at boot
# (restarted automatically if it stops). Output goes to logs\elramy.log.

. "$PSScriptRoot\settings.ps1"

$env:JWT_SECRET  = Read-Secret 'jwt.secret'
$env:DB_URL      = "jdbc:postgresql://localhost:$(Get-DbPort)/$DbName"
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = Read-Secret 'db.secret'
$adminFile = Join-Path $SecretsDir 'admin-initial.secret'
if (Test-Path $adminFile) { $env:ADMIN_INITIAL_PASSWORD = Read-Secret 'admin-initial.secret' }

$log = Join-Path $LogsDir 'elramy.log'
# Keep the log small: start a new one past 20 MB (one old copy kept).
if ((Test-Path $log) -and ((Get-Item $log).Length -gt 20MB)) { Move-Item $log "$log.old" -Force }

$java = (Get-Content (Join-Path $AppDir 'java-path.txt') -Raw).Trim()
$jar  = Join-Path $AppDir 'elramy.jar'
# Only this laptop can open the store (127.0.0.1); cmd does the log redirect
# so Java's normal console output is not treated as a PowerShell error.
& cmd /c "`"$java`" -Xmx512m -Dfile.encoding=UTF-8 -jar `"$jar`" --server.port=$Port --server.address=127.0.0.1 >> `"$log`" 2>&1"
exit $LASTEXITCODE
