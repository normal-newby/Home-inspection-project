@echo off
title Home Inspection App

wsl docker info >nul 2>&1
if not errorlevel 1 (
    echo Docker daemon already running.
    goto dockerready
)

echo Starting Docker service...
wsl sudo service docker start

echo Waiting for Docker daemon...
set dattempts=0
:dockerwait
set /a dattempts+=1
if %dattempts% gtr 30 (
    echo ERROR: Docker daemon did not start after 60 seconds.
    pause
    exit /b 1
)
wsl docker info >nul 2>&1
if errorlevel 1 (
    timeout /t 2 >nul
    goto dockerwait
)
:dockerready
echo Starting containers...
start "Docker" cmd /k "wsl docker compose up --build"

echo Waiting for app to start...
set attempts=0
:appwait
set /a attempts+=1
if %attempts% gtr 60 (
    echo ERROR: App did not start after 120 seconds.
    pause
    exit /b 1
)
timeout /t 2 >nul
curl -s http://localhost:8080 >nul 2>&1
if errorlevel 1 goto appwait

echo App is ready!
start http://localhost:8080
echo.
echo App is running at http://localhost:8080
echo Close the Docker window to stop the app.
pause

