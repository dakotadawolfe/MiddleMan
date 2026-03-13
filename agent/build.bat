@echo off
setlocal
set SRC=src\main\java
set OUT=build\classes
set JAR=build\MiddleManAgent.jar
set MANIFEST=build\manifest.txt

if not exist "%OUT%" mkdir "%OUT%"
if not exist "build" mkdir "build"

echo Compiling (Java 11 bytecode for RuneLite JRE)...
javac -source 11 -target 11 --add-modules jdk.attach -d "%OUT%" -encoding UTF-8 %SRC%\middleman\agent\*.java
if errorlevel 1 exit /b 1

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
