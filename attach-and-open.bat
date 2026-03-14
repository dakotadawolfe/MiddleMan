@echo off
cd /d "%~dp0\.."
setlocal enabledelayedexpansion
set "AGENT_DIR=%~dp0agent"
set "BUILD_DIR=%AGENT_DIR%\build"
echo [1/3] Building agent (unique JAR per run so in-use JARs are never overwritten)...
cd /d "%AGENT_DIR%"
set DTS=00000000000000
for /f "skip=1 tokens=1" %%a in ('wmic os get localdatetime 2^>nul') do set "DTS=%%a"
set "DTS=!DTS:~0,14!"
set "JARNAME=MiddleManAgent-!DTS!-!RANDOM!.jar"
set "JAR=%BUILD_DIR%\!JARNAME!"
call "%AGENT_DIR%\build.bat" "!JARNAME!"
set BUILDERR=!errorlevel!
cd /d "%~dp0\.."
if not "!BUILDERR!"=="0" (
    echo Build failed. Make sure JAVA_HOME is set to your JDK.
    pause
    exit /b 1
)
if not exist "!JAR!" (
    echo Agent JAR not found at !JAR!
    pause
    exit /b 1
)
echo Built and will attach: !JAR!
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=!JAVA_HOME!\bin\java.exe"
set "JAR_VER="
for /f "delims=" %%v in ('"!JAVA_EXE!" -cp "!JAR!" middleman.agent.AgentVersion 2^>nul') do set "JAR_VER=%%v" & echo Version in built JAR: %%v

echo [2/3] Looking for RuneLite...
set PID=
for /f "delims=" %%a in ('powershell -NoProfile -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and $_.CommandLine -match 'runelite' } | Select-Object -First 1 -ExpandProperty ProcessId"') do set PID=%%a
if not defined PID for /f "tokens=1" %%a in ('wmic process where "name='java.exe' or name='javaw.exe'" get processid^,commandline 2^>nul ^| findstr /i runelite') do set PID=%%a

if defined PID (
    echo Attaching to RuneLite PID !PID!...
    if not exist "!JAVA_EXE!" set "JAVA_EXE=java"
    set "DEBUGLOG=%~dp0debug-01c49b.log"
    "!JAVA_EXE!" --add-modules jdk.attach -cp "!JAR!" middleman.agent.AttachMain !PID! "!JAR!" "!DEBUGLOG!" "!JAR_VER!"
    if errorlevel 1 (
        echo Attach failed. Need a JDK with jdk.attach: set JAVA_HOME to your JDK and try again.
        echo Check MiddleMan\middleman.log to see if the agent started.
    ) else (
        echo Attached. Wait a few seconds for the API to start, then refresh the dashboard.
    )
) else (
    echo RuneLite not running. Start it with: MiddleMan\launcher\launch-attachable.bat
    echo Then run this batch again to attach.
)

echo [3/3] Opening dashboard...
if defined PID timeout /t 5 /nobreak >nul
start "" "%~dp0dashboard\index.html"
echo Done.
pause
