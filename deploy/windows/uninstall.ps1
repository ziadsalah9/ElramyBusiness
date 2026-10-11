#Requires -RunAsAdministrator
# Stops the store and removes its scheduled tasks and shortcut.
# Data, backups and secrets in C:\Elramy and the database are KEPT.
. "$PSScriptRoot\settings.ps1"
Stop-ElramyApp
Unregister-ScheduledTask -TaskName $AppTask -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName $BackupTask -Confirm:$false -ErrorAction SilentlyContinue
Remove-Item 'C:\Users\Public\Desktop\Elramy.url' -ErrorAction SilentlyContinue
Write-Host "Removed the tasks and shortcut. Data is still in $ElramyRoot and in PostgreSQL."
