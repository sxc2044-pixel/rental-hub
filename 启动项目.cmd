@echo off
chcp 65001 >nul
cd /d "%~dp0"
set SPRING_PROFILES_ACTIVE=mysql
set PORT=8080
if not exist "config\application-local.properties" (
 copy "config\application-local.example.properties" "config\application-local.properties" >nul
 echo 已创建数据库配置。请填写 config\application-local.properties 中的密码后重新启动。
 notepad "config\application-local.properties"
 pause
 exit /b 1
)
echo 校园安居将在 Windows 本机启动，数据库为 Kali 的 192.168.93.128:3306/kali。
echo 启动成功后访问 http://127.0.0.1:8080 。请保留此窗口。
if exist "dist\rental-hub-1.0.0.jar" (
 java -Dfile.encoding=UTF-8 -jar "dist\rental-hub-1.0.0.jar"
) else if exist "target\rental-hub-1.0.0.jar" (
 java -Dfile.encoding=UTF-8 -jar "target\rental-hub-1.0.0.jar"
) else (
 echo 请先执行 mvn package，或使用包含 dist 的完整交付包。
)
pause
