@echo off
wsl docker compose up --build -d
echo Waiting for app to start...
:loop
timeout /t 2 >nul
curl -s http://localhost:8080 >nul 2>&1
if errorlevel 1 goto loop
start http://localhost:8080