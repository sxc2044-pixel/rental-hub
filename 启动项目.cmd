@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" -Profile mysql -Port 8080 -OpenBrowser
if errorlevel 1 (
 echo 启动未完成，请查看上方提示；应用日志位于 logs\rental-hub.log。
 pause
 exit /b 1
)
exit /b 0
