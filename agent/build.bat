@echo off
setlocal enabledelayedexpansion
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set SRC=src\main\java
set OUT=build\classes
set JAR=build\MiddleManAgent.jar
if not "%~1"=="" set JAR=build\%~1
set MANIFEST=build\manifest.txt

if not exist "build" mkdir "build"
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

set VERSION=4.0-hotreload-test
if exist "version.txt" for /f "usebackq delims=" %%a in ("version.txt") do set "VERSION=%%a" & goto :version_done
:version_done
set "VERSION=!VERSION: "=!"
echo Injecting version: [!VERSION!]
mkdir "build\gensrc\middleman\agent" 2>nul
del /q "build\gensrc\middleman\agent\AgentVersion.java" 2>nul
echo package middleman.agent;> "build\gensrc\middleman\agent\AgentVersion.java"
echo public final class AgentVersion { public static final String V = "!VERSION!"; public static void main(String[] a^) { System.out.println(V^); } }>> "build\gensrc\middleman\agent\AgentVersion.java"

echo Compiling (Java 11 bytecode for RuneLite JRE)...
javac -source 11 -target 11 --add-modules jdk.attach -d "%OUT%" -encoding UTF-8 -sourcepath "%SRC%;build\gensrc" build\gensrc\middleman\agent\AgentVersion.java %SRC%\middleman\agent\*.java
if errorlevel 1 exit /b 1

set TSTAMP=%date% %time%
echo !TSTAMP!> "%OUT%\buildtime.txt"

echo Premain-Class: middleman.agent.MiddleManAgent> "%MANIFEST%"
echo Agent-Class: middleman.agent.MiddleManAgent>> "%MANIFEST%"
echo Main-Class: middleman.agent.AttachMain>> "%MANIFEST%"
echo Can-Redefine-Classes: false>> "%MANIFEST%"
echo Can-Retransform-Classes: false>> "%MANIFEST%"
echo.>> "%MANIFEST%"

echo Building JAR...
jar cfm "%JAR%" "%MANIFEST%" -C "%OUT%" .
if errorlevel 1 exit /b 1

echo Built %JAR%
endlocal
