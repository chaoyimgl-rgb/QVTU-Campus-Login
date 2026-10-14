@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist build\policy-tests mkdir build\policy-tests
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -d build\policy-tests app\src\main\java\cn\qvtu\campuslite\PortalPolicy.java app\src\main\java\cn\qvtu\campuslite\Observation.java app\src\main\java\cn\qvtu\campuslite\RollingLog.java tests\PortalPolicyTest.java tests\ObservationTest.java app\src\main\java\cn\qvtu\campuslite\GuardPolicy.java tests\GuardPolicyTest.java
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java.exe" -cp build\policy-tests cn.qvtu.campuslite.PortalPolicyTest
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java.exe" -cp build\policy-tests cn.qvtu.campuslite.ObservationTest
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java.exe" -cp build\policy-tests cn.qvtu.campuslite.GuardPolicyTest
exit /b %ERRORLEVEL%
