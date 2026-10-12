#define AppName "QVTU Campus Login"
#define AppVersion "1.0.0"
#define ConfigExe "QVTLogin_Config.exe"
#define LoginExe "QVTLogin.exe"

[Setup]
AppId={{34B3A9A9-455E-4E81-8B73-5BCB2F8D29F3}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=QVTU Campus Login contributors
DefaultDirName={autopf}\QVTU Campus Login
DefaultGroupName=QVTU Campus Login
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
OutputDir=output
OutputBaseFilename=QVTU-Campus-Login-Setup-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#ConfigExe}

[Files]
Source: "staging\{#ConfigExe}"; DestDir: "{app}"; Flags: ignoreversion
Source: "staging\{#LoginExe}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\QVTU 校园网设置"; Filename: "{app}\{#ConfigExe}"
Name: "{commondesktop}\QVTU 校园网设置"; Filename: "{app}\{#ConfigExe}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "创建桌面设置快捷方式"; GroupDescription: "附加快捷方式："; Flags: unchecked

[Run]
Filename: "{app}\{#ConfigExe}"; Description: "打开 QVTU 校园网设置"; Flags: postinstall nowait skipifsilent

[UninstallDelete]
Type: files; Name: "{userstartup}\{#LoginExe}"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  SourcePath, StartupPath: string;
begin
  if CurStep = ssPostInstall then
  begin
    SourcePath := ExpandConstant('{app}\{#LoginExe}');
    StartupPath := ExpandConstant('{userstartup}\{#LoginExe}');
    if FileExists(StartupPath) then
      DeleteFile(StartupPath);
    if not FileCopy(SourcePath, StartupPath, False) then
      MsgBox('无法把 QVTLogin.exe 复制到当前用户的 Startup 文件夹。请检查用户目录权限。', mbError, MB_OK);
    RegDeleteValue(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Run', 'QVTU校园网登录');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    DeleteFile(ExpandConstant('{userstartup}\{#LoginExe}'));
    RegDeleteValue(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Run', 'QVTU校园网登录');
  end;
end;
