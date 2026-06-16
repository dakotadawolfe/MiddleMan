@echo off
REM Start RuneLite without the agent and without DisableAttachMechanism.
REM Then run MiddleMan.exe to attach the agent and open the dashboard.
cd /d "%~dp0\..\.."
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0launch.ps1" -AttachableOnly
