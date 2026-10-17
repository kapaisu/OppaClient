#define AppVersion GetEnv("OPPA_VERSION")

[Setup]
AppId={{8E4A6C2B-5D1F-4F3A-9C7E-2B6D0A9F41C3}
AppName=Oppa
AppVersion={#AppVersion}
AppPublisher=kapaisu
AppPublisherURL=https://github.com/kapaisu/OppaClient
AppSupportURL=https://github.com/kapaisu/OppaClient/issues
DefaultDirName={autopf}\Oppa
DisableProgramGroupPage=yes
LicenseFile=..\..\LICENSE
OutputDir=..\..\dist
OutputBaseFilename=Oppa-Setup
SetupIconFile=oppa.ico
UninstallDisplayIcon={app}\bin\oppa.exe
UninstallDisplayName=Oppa
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequiredOverridesAllowed=dialog

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"

[Files]
Source: "..\..\dist\windows\Oppa\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion
Source: "vc_redist.x64.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall; Check: NeedsRuntime

[Icons]
Name: "{autoprograms}\Oppa"; Filename: "{app}\bin\oppa.exe"
Name: "{autodesktop}\Oppa"; Filename: "{app}\bin\oppa.exe"; Tasks: desktopicon

[Run]
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing the Microsoft Visual C++ runtime..."; Check: NeedsRuntime
Filename: "{app}\bin\oppa.exe"; Description: "Open Oppa"; Flags: nowait postinstall skipifsilent

[Code]
function NeedsRuntime: Boolean;
var
  Installed: Cardinal;
begin
  Result := not (RegQueryDWordValue(HKLM64, 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Installed', Installed) and (Installed = 1));
end;
