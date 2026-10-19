@echo off
rem Lanzador de setup-host.ps1 (doble clic o desde la consola; acepta los mismos parametros)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-host.ps1" %*
if errorlevel 1 pause
