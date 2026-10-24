# Kali 数据库连接和备份

Windows本机运行Java网站，默认通过JDBC连接Kali的MySQL：192.168.93.128:3306/kali，用户名root。本机Navicat是独立数据库管理客户端，不是数据库服务；它与Java分别连接同一个Kali数据库。CMD/Java入口读取私有 `config/application-local.properties`，PowerShell入口读取私有 `config/local.ps1`，两份本机配置均已填写，且不进入Git或可分享压缩包。本次按用户指示暂缓Kali联调，因此以下是部署步骤，不能作为已通过远程验收的证据。

## 切换远程数据库

1. Kali数据库名为 `kali`，地址为 `192.168.93.128:3306`。确认与Windows处于可互访的虚拟网段。Kali常见默认数据库为MariaDB，应用同时提供mysql和mariadb配置，应以数据库实际产品为准。
2. Kali执行 `sudo systemctl status mysql` 或 `sudo systemctl status mariadb`，再执行 `sudo ss -lntp | grep 3306` 查看是否监听虚拟网卡IP。若只监听127.0.0.1，按实际MySQL/MariaDB配置文件设置 `bind-address` 为虚拟网卡地址并重启数据库服务。
3. 以管理员身份执行 `docs/数据库部署.sql`。推荐项目专用用户，仅允许Windows主机IP访问，不要在公网开放root账号。
4. 双击 `启动项目.cmd`。分享包首次运行会由 `config/application-local.example.properties` 创建私有配置并打开记事本，填写密码后重新启动。若使用PowerShell，则复制 `config/local.example.ps1` 为 `config/local.ps1` 并填写数据库密码。MySQL使用 `SPRING_PROFILES_ACTIVE=mysql`，MariaDB使用 `mariadb`。首次无管理员账号时设置 `ADMIN_PASSWORD`。需要示例数据时，将 `SEED_DEMO` 设为true。
5. 执行 `./scripts/start.ps1 -Profile local`。浏览器访问 `http://127.0.0.1:8080`。服务和界面在Windows运行，只有数据库位于虚拟机。

数据库表通过 `schema.sql` 创建，索引由 `Bootstrap` 通过元数据检查后创建。不要将项目直接连接至已有不兼容的同名表。数据表升级应先备份并编写明确的迁移SQL。

## 独立H2验证模式的备份与恢复

Kali不可连接时，显式运行 `启动演示.cmd` 或 `scripts/start.ps1 -Profile demo`。此时数据保存在本机H2库，不会同步到Kali。常规MySQL启动不会自动回退到H2。

程序运行期间每天北京时间02:00自动备份H2数据库和上传图片。也可在管理员的“操作审计”页面点击“备份本机数据”。文件位于 `data/backups/rental-backup-时间.zip`，内含在线一致性数据库快照 `database.zip` 与 `uploads`。备份可能包含认证材料，应保存在受控目录。

运行 `./scripts/restore-demo.ps1 -Archive './data/backups/备份文件.zip' -OutputDirectory './restored-demo'` 可恢复到新的项目内目录，并查询用户、房源、预约计数核验。该脚本拒绝覆盖现有恢复目录，不修改当前运行数据库。若需要切换，先停止应用，再保存当前数据、将恢复的数据库和上传目录切换到应用配置目录。不要复制正在写入的 `.mv.db` 文件充当备份。

## Kali每日备份

在Kali创建 `~/.my-rental.cnf`：

```ini
[client]
user=rental_app
password=填写真实密码
host=127.0.0.1
port=3306
```

执行 `chmod 600 ~/.my-rental.cnf` 和 `chmod 700 scripts/backup-mysql.sh`。运行脚本会生成一致性SQL备份、gzip校验和SHA256文件。根据需要安装 `mysql-client` 或 `mariadb-client`；MariaDB环境可将脚本中的mysqldump命令调整为mariadb-dump。

每天02:00运行的cron示例：

```cron
0 2 * * * /绝对路径/scripts/backup-mysql.sh >> /受控目录/rental-backup.log 2>&1
```

恢复前应先停止应用并导出当前状态，在隔离测试库验证备份，再决定是否导入目标库。恢复命令示例 `gzip -dc backup.sql.gz | mysql --defaults-extra-file=~/.my-rental.cnf`。脚本备份包含CREATE DATABASE及USE语句，导入将指向备份中的数据库，必须先检查文件内容。上传图片保存在Windows本机，应同时备份 `data/uploads`，并与SQL快照对应归档。

备份保留期限、异地介质、恢复时间目标和容许数据损失仍需按使用方要求确定。未把99%长期可用性写成已经测得的结果。
