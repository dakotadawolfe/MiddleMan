@echo off
REM Launcher that bypasses PowerShell execution policy (no admin required).
REM Run from RuneLite project root: .\MiddleMan\launcher\launch.bat
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0launch.ps1"
