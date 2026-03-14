@echo off
cd /d "%~dp0\.."
setlocal enabledelayedexpansion
set "AGENT_DIR=%~dp0agent"
set "BUILD_DIR=%AGENT_DIR%\build"
set "DBG=%~dp0..\debug-01c49b.log"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo Pruning old unlocked agent JARs...
for %%f in ("%BUILD_DIR%\MiddleManAgent-*.jar") do (
    del /q "%%~f" >nul 2>nul
)

echo [1/3] Building agent (unique JAR per run so in-use JARs are never overwritten)...
cd /d "%AGENT_DIR%"
set "DTS="
rem #region agent log
>>"%DBG%" echo {"sessionId":"01c49b","runId":"dtsgen","hypothesisId":"H1,H2","location":"attach-and-open.bat:15","message":"dts_init","data":{"DTS":"!DTS!"},"timestamp":%time:~0,2%%time:~3,2%%time:~6,2%%time:~9,2%}
rem #endregion
for /f "skip=1 tokens=1" %%a in ('wmic os get localdatetime 2^>nul') do if not defined DTS set "DTS=%%a"
if not defined DTS for /f "delims=" %%a in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyyMMddHHmmss\")" 2^>nul') do if not defined DTS set "DTS=%%a"
rem #region agent log
>>"%DBG%" echo {"sessionId":"01c49b","runId":"dtsgen","hypothesisId":"H1,H2","location":"attach-and-open.bat:17","message":"dts_after_wmic","data":{"DTS":"!DTS!"},"timestamp":%time:~0,2%%time:~3,2%%time:~6,2%%time:~9,2%}
rem #endregion
if not defined DTS set "DTS=00000000000000"
set "DTS=!DTS:~0,14!"
if "!DTS!"=="" set "DTS=00000000000000"
set "JARNAME=MiddleManAgent-!DTS!-!RANDOM!.jar"
set "JAR=%BUILD_DIR%\!JARNAME!"
rem #region agent log
>>"%DBG%" echo {"sessionId":"01c49b","runId":"dtsgen","hypothesisId":"H2,H3","location":"attach-and-open.bat:21","message":"jar_name_computed","data":{"DTS":"!DTS!","JARNAME":"!JARNAME!","JAR":"!JAR!"},"timestamp":%time:~0,2%%time:~3,2%%time:~6,2%%time:~9,2%}
rem #endregion
call "%AGENT_DIR%\build.bat" "!JARNAME!"
set BUILDERR=!errorlevel!
cd /d "%~dp0\.."
if not "!BUILDERR!"=="0" (
    echo Build failed. Make sure JAVA_HOME is set to your JDK.
    exit /b 1
)
if not exist "!JAR!" (
    rem #region agent log
    >>"%DBG%" echo {"sessionId":"01c49b","runId":"dtsgen","hypothesisId":"H3,H4","location":"attach-and-open.bat:33","message":"jar_missing","data":{"JARNAME":"!JARNAME!","JAR":"!JAR!"},"timestamp":%time:~0,2%%time:~3,2%%time:~6,2%%time:~9,2%}
    rem #endregion
    echo Agent JAR not found at !JAR!
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

if not defined PID goto :no_pid

echo Attaching to RuneLite PID !PID!...
if not exist "!JAVA_EXE!" set "JAVA_EXE=java"
"!JAVA_EXE!" --add-modules jdk.attach -cp "!JAR!" middleman.agent.AttachMain !PID! "!JAR!" "!JAR_VER!"
if errorlevel 1 (
    echo Attach failed. Need a JDK with jdk.attach: set JAVA_HOME to your JDK and try again.
    echo Check MiddleMan\middleman.log to see if the agent started.
) else (
    echo Attached. Wait a few seconds for the API to start, then refresh the dashboard.
)
goto :post_attach

:no_pid
echo RuneLite not running. Start it with: MiddleMan\launcher\launch-attachable.bat
echo Then run this batch again to attach.

:post_attach
if not defined PID goto :prune_all
echo Pruning old unlocked agent JARs (keeping latest)...
for %%f in ("%BUILD_DIR%\MiddleManAgent-*.jar") do (
    if /I not "%%~nxf"=="!JARNAME!" del /q "%%~f" >nul 2>nul
)
goto :open_dash

:prune_all
echo RuneLite closed; pruning generated agent JARs...
del /q "%BUILD_DIR%\MiddleManAgent-*.jar" >nul 2>nul

:open_dash

echo [3/3] Opening dashboard...
if defined PID timeout /t 5 /nobreak >nul
start "" "%~dp0dashboard\index.html"
echo Done.
exit /b 0
