@echo off
setlocal EnableExtensions
cd /d "%~dp0"
where cmake >nul 2>nul
if errorlevel 1 (
  echo CMake was not found. Install CMake or use a Visual Studio Developer Command Prompt.
  pause
  exit /b 1
)
if not defined WEBVIEW2_SDK (
  echo Set WEBVIEW2_SDK to the WebView2 Native SDK build\native folder.
  echo Example: set WEBVIEW2_SDK=%%USERPROFILE%%\.nuget\packages\microsoft.web.webview2\1.0.2903.40\build\native
  pause
  exit /b 1
)
cmake -S . -B build -A x64 -DWEBVIEW2_SDK="%WEBVIEW2_SDK%"
if errorlevel 1 goto failed
cmake --build build --config Release --parallel 2
if errorlevel 1 goto failed
echo Build complete: build\bin\Release\QVTLogin_Config.exe and QVTLogin.exe
pause
exit /b 0
:failed
echo Build failed. Review the output above.
pause
exit /b 1
