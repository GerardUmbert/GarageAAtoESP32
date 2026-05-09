@echo off
powershell.exe -ExecutionPolicy Bypass -File "%~dp0flash.ps1" %*
pause
