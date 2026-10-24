-- 在Kali管理员客户端按需执行；应用正常启动会自动建立业务表与索引。
-- 已有数据库不会删除。请先检查现有同名t_表，避免将应用连接到不兼容的业务库。
CREATE DATABASE IF NOT EXISTS rental CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 推荐创建项目专用账号。将 WINDOWS_HOST_IP 与 CHANGE_THIS_PASSWORD 替换为真实值。
-- CREATE USER IF NOT EXISTS 'rental_app'@'WINDOWS_HOST_IP' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON rental.* TO 'rental_app'@'WINDOWS_HOST_IP';
-- FLUSH PRIVILEGES;
-- 表结构权威来源：src/main/resources/schema.sql。
