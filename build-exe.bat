@echo off
setlocal
cd /d "%~dp0"
set OUT=MiddleMan.exe
set "CSC64=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "CSC32=C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if exist "%CSC64%" set "CSC=%CSC64%" & goto :build
if exist "%CSC32%" set "CSC=%CSC32%" & goto :build
echo csc.exe not found. Install .NET Framework.
exit /b 1
:build
echo Building %OUT%...
"%CSC%" /nologo /target:winexe /out:"%OUT%" /r:System.Management.dll OpenDashboard.cs
if errorlevel 1 exit /b 1
echo Built %OUT% — run it from this folder to open the dashboard.
endlocal
