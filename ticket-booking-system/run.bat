@echo off
setlocal

cd /d "%~dp0"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=cli"

if /i "%MODE%"=="cli" (
    set "MAIN_CLASS=ticketbooking.Main"
) else if /i "%MODE%"=="gui" (
    set "MAIN_CLASS=ticketbooking.ui.GuiMain"
) else (
    echo Usage: run.bat [cli^|gui]
    exit /b 2
)

where javac >nul 2>nul
if errorlevel 1 (
    echo Error: javac not found. Install JDK 17 or newer and add it to PATH.
    exit /b 1
)

if exist out rmdir /s /q out
mkdir out

set "SOURCES=%TEMP%\ticketbooking-sources.txt"
dir /s /b src\*.java > "%SOURCES%"

javac --release 17 -d out @"%SOURCES%"
set "RESULT=%ERRORLEVEL%"
del "%SOURCES%" >nul 2>nul
if not "%RESULT%"=="0" exit /b %RESULT%

java -cp out %MAIN_CLASS%
