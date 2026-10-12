@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "CONFIG=%~dp0build\bin\Release\QVTLogin_Config.exe"
set "LOGIN=%~dp0build\bin\Release\QVTLogin.exe"
if not exist "%CONFIG%" echo Missing %CONFIG%. Build the C++ targets first. & pause & exit /b 1
if not exist "%LOGIN%" echo Missing %LOGIN%. Build the C++ targets first. & pause & exit /b 1
copy /Y "%CONFIG%" "installer\staging\QVTLogin_Config.exe" >nul
if errorlevel 1 pause & exit /b 1
copy /Y "%LOGIN%" "installer\staging\QVTLogin.exe" >nul
if errorlevel 1 pause & exit /b 1
set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" echo Inno Setup 6 was not found. Install it, then compile installer\QVTUCampusLogin.iss. & pause & exit /b 1
"%ISCC%" "installer\QVTUCampusLogin.iss"
if errorlevel 1 pause & exit /b 1
echo Installer created under installer\output.
pause
