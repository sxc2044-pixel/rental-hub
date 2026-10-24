$env:SPRING_PROFILES_ACTIVE = 'mysql' # Kali 若运行 MariaDB，可改为 mariadb
$env:DB_HOST = '192.168.93.128'
$env:DB_PORT = '3306'
$env:DB_NAME = 'kali'
$env:DB_USER = 'rental_app'
$env:DB_PASSWORD = '请填写数据库密码'
$env:ADMIN_PASSWORD = '请设置至少8位含字母数字的管理员密码'
$env:SEED_DEMO = 'false' # 课程演示需要示例数据时可改为 true
