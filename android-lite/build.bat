@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java not found. Set JAVA_HOME to Android Studio JBR or JDK 17+.
  exit /b 1
)
if not exist "%ANDROID_HOME%\platforms\android-34\android.jar" (
  echo android-34 SDK not found. Set ANDROID_HOME to an existing SDK.
  exit /b 1
)
call "%~dp0gradlew.bat" --no-daemon assembleDebug lintDebug %*
exit /b %ERRORLEVEL%
