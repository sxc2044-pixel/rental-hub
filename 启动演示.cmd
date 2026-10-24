@echo off
chcp 65001 >nul
cd /d "%~dp0"
set SPRING_PROFILES_ACTIVE=demo
set PORT=8080
echo 校园安居将在本机启动，请保留此窗口。
echo 启动完成后浏览器访问 http://127.0.0.1:8080
echo 学生账号 2023100513  密码 Student123!
echo 管理员账号 admin  密码 Admin123!
if exist "dist\rental-hub-1.0.0.jar" (
 java -Dfile.encoding=UTF-8 -jar "dist\rental-hub-1.0.0.jar"
) else if exist "target\rental-hub-1.0.0.jar" (
 java -Dfile.encoding=UTF-8 -jar "target\rental-hub-1.0.0.jar"
) else (
 echo 未找到运行包。请先执行 mvn package，或使用包含dist的完整交付包。
)
pause
