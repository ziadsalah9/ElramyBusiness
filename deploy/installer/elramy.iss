; Elramy store - Windows installer (Inno Setup 6).
; Built on the Mac by deploy/build-installer.sh (Inno Setup in Docker).
; Installs everything the store needs into C:\Elramy, with no internet:
;   app\   the store (Spring Boot jar serving the web app) + scripts
;   jre\   Java 21 runtime (Eclipse Temurin), private to the store
;   pgsql\ PostgreSQL 16 (EDB binaries), run as the "ElramyPostgreSQL" service
; then runs app\setup-elramy.ps1 (database, secrets, autostart, backups).
; Uninstall removes the program but KEEPS pgdata, backups and secrets.

#ifndef AppVersion
  #define AppVersion "1.0.0.0"
#endif

[Setup]
AppId={{1AAE0365-7634-4698-A5D2-8DA519446ADD}
AppName=Elramy Store
AppVerName=Elramy Store {#AppVersion}
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}
AppPublisher=Elramy
DefaultDirName=C:\Elramy
DisableDirPage=yes
DisableProgramGroupPage=yes
DefaultGroupName=Elramy
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir=out
OutputBaseFilename=Elramy-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
SetupLogging=yes
UninstallDisplayName=Elramy Store
CloseApplications=no

[Languages]
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
ar.SettingUp=جاري تجهيز قاعدة البيانات وتشغيل البرنامج (قد يستغرق دقيقة)...
en.SettingUp=Setting up the database and starting the store (may take a minute)...
ar.SetupFailed=لم يكتمل تجهيز البرنامج. أرسل هذا الملف للدعم:
en.SetupFailed=The store could not be set up. Send this file to support:
ar.OpenStore=فتح البرنامج
en.OpenStore=Open the store
ar.ShowLogin=عرض بيانات أول دخول
en.ShowLogin=Show the first login
ar.RestoreBackup=استرجاع نسخة احتياطية
en.RestoreBackup=Restore a backup
ar.BackupNow=نسخة احتياطية الآن
en.BackupNow=Back up now
ar.Logs=سجلات البرنامج
en.Logs=Store logs

[Files]
Source: "stage\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "stage\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "stage\pgsql\*"; DestDir: "{app}\pgsql"; Flags: ignoreversion recursesubdirs createallsubdirs
; Used before files are replaced (upgrade): back up, stop the app and the database.
Source: "stage\app\stop-elramy.ps1"; Flags: dontcopy

[INI]
Filename: "{app}\Elramy.url"; Section: "InternetShortcut"; Key: "URL"; String: "http://localhost:8090/"

[Icons]
Name: "{commondesktop}\Elramy"; Filename: "{app}\Elramy.url"
Name: "{group}\Elramy"; Filename: "{app}\Elramy.url"
Name: "{group}\{cm:RestoreBackup}"; Filename: "{app}\app\restore.bat"
Name: "{group}\{cm:BackupNow}"; Filename: "{app}\app\backup-now.bat"
Name: "{group}\{cm:Logs}"; Filename: "{app}\logs"

[Run]
Filename: "notepad.exe"; Parameters: """{app}\FIRST-LOGIN.txt"""; Description: "{cm:ShowLogin}"; Flags: postinstall nowait skipifsilent; Check: HasFirstLogin
Filename: "http://localhost:8090/"; Description: "{cm:OpenStore}"; Flags: postinstall shellexec nowait skipifsilent

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\app\stop-elramy.ps1"" -Unregister"; Flags: runhidden waituntilterminated; RunOnceId: "StopElramy"

[UninstallDelete]
Type: files; Name: "{app}\Elramy.url"

[Code]
function HasFirstLogin: Boolean;
begin
  Result := FileExists(ExpandConstant('{app}\FIRST-LOGIN.txt'));
end;

{ Upgrade: back up the data and stop the running store before files are replaced. }
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  ExtractTemporaryFile('stop-elramy.ps1');
  Exec('powershell.exe', '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{tmp}\stop-elramy.ps1') + '" -Backup',
       '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

{ After the files are copied: database, secrets, autostart, backups, start. }
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := CustomMessage('SettingUp');
    if not Exec('powershell.exe', '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\app\setup-elramy.ps1') + '"',
                '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
      MsgBox(CustomMessage('SetupFailed') + #13#10 + ExpandConstant('{app}\logs\setup.log'), mbError, MB_OK);
  end;
end;
