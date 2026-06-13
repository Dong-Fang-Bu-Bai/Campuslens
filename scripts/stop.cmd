@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0campuslens.ps1" -Action Stop
exit /b %errorlevel%
