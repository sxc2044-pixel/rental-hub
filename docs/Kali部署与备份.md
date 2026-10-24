# Kali 数据库连接和备份

Windows本机运行Java网站，Navicat独立管理Kali数据库。实际服务器为MariaDB 11.4.5，应用的mysql profile通过兼容协议连接，目标为192.168.93.128:3306/rental，用户名root。

CMD和PowerShell统一读取私有 `config/application-local.properties`，分享包只提供 `.example.properties` 模板。旧的local.ps1两套配置已归档，避免修改一处而另一入口仍使用旧参数。

本次连接握手约需10.5秒，超过原10秒读取超时；修复为30秒后，网站已连接真实Kali数据库并通过10组浏览器流程检查。服务器skip_name_resolve=0可能与握手等待有关，本次没有修改数据库全局DNS或授权设置。

按用户要求，原kali库的10张表及全部记录迁移至rental并逐表校验；旧rental_db仅含初始化管理员、12条示例房源、14条字典及1条公告，无预约、合同、评价、咨询等业务记录，已完整备份后删除。私有备份位于工作区 `.work/database-backups/20261024-rename-rental/`，不进入分享包。

## 首次部署

1. Navicat连接192.168.93.128:3306，执行docs/数据库部署.sql，创建rental数据库。
2. 双击启动项目.cmd。分享包首次运行会创建私有配置，填写数据库密码后再启动。
3. 默认约需15秒启动，看到日志完成后访问http://127.0.0.1:8080。保留Java进程，打开Kali或Navicat本身不会启动网站。
4. 数据库表由schema.sql创建，索引由Bootstrap检查。已有同名不兼容表不会自动迁移。

## 独立H2验证模式的备份与恢复

Kali不可连接时，显式运行 `scripts/start.ps1 -Profile demo`。此时数据保存在本机H2库，不会同步到Kali。常规MySQL启动不会自动回退到H2。

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
