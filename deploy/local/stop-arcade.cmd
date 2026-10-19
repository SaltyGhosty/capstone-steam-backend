@echo off
rem Lanzador de stop-arcade.ps1 (doble clic o desde la consola; acepta los mismos parametros)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-arcade.ps1" %*
if errorlevel 1 pause
