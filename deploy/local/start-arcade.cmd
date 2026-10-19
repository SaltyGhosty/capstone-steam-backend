@echo off
rem Lanzador de start-arcade.ps1 (doble clic o desde la consola; acepta los mismos parametros)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-arcade.ps1" %*
if errorlevel 1 pause
