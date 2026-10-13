@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist build\policy-tests mkdir build\policy-tests
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -d build\policy-tests app\src\main\java\cn\qvtu\campuslite\PortalPolicy.java tests\PortalPolicyTest.java
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java.exe" -cp build\policy-tests cn.qvtu.campuslite.PortalPolicyTest
exit /b %ERRORLEVEL%
