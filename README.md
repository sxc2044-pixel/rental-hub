# 校园安居 租房信息管理系统

基于随附需求规格说明书实现的Java 21课程项目，覆盖学生租客、房东与管理员三类角色、FR-01至FR-17。Web应用在Windows本机运行，默认通过JDBC连接Kali的MySQL数据库。Navicat仅用于管理同一数据库，不需要启动单独的本机MySQL。H2保留为显式选择的离线验证模式。业务代码为本项目原创实现，未复制第三方租房项目。

## 快速运行

确保安装JDK 21，双击 **启动项目.cmd**。本机私有连接配置已经填写；分享包首次启动会创建并打开 `config/application-local.properties`，填写数据库密码后重新启动。目标为 `192.168.93.128:3306/rental`，用户名 `root`。启动成功后访问 [校园安居](http://127.0.0.1:8080)。本机dist存放可运行JAR。最终源码分享包不包含编译产物，首次启动需要Maven构建并下载依赖。

CMD调用统一的PowerShell入口：网站已经运行时会直接打开现有网站，连续双击时等待原窗口完成启动，不重复创建Java服务。若端口由其他程序占用，会明确提示而不终止对方进程。切换数据库或运行模式前，先在原服务窗口按Ctrl+C停止网站。

验证码为本机应用生成的简单加法题。当前数据库的完整账号与房源归属见下表；这些均为课程演示账号。注册时只展示一次密码恢复密钥，请自行保存。原演示学生恢复密钥为 `demo-recovery-2026`，本次未重置任何已有账号的密码或恢复密钥。

从源码运行：

```powershell
mvn -B -ntp verify
java '-Dfile.encoding=UTF-8' -jar target/rental-hub-1.0.0.jar
```

也可运行 `./scripts/start.ps1`，默认加载本机私有远程连接配置。Kali暂时不可用时，可显式运行 `./scripts/start.ps1 -Profile demo`，使用独立H2文件库验证页面；常规启动不会自动回退数据库。PowerShell脚本若受本机执行策略限制，可使用CMD启动脚本或直接运行Java命令，无需更改全局系统策略。

默认监听127.0.0.1:8080，按Ctrl+C停止。端口占用时设置 `PORT=8081` 后使用Java启动，或运行 `./scripts/start.ps1 -Profile demo -Port 8081`。MySQL业务数据保存在Kali，上传文件位于Windows项目 `data/uploads`，日志位于 `logs`；H2模式的数据库位于 `data`。再次启动保留已有数据，不重复覆盖示例房源。



## 全流程测试数据

以下清单对应2026年11月4日清理后的Kali `rental` 数据库，不代表全新空库会自动生成同样的账号。已有27个账号保持原样，根据34套房源原有的“示例房东 / 13800138000”联系人建立1个对应房东账号，并完成房源归属。当前共28个账号、35套房源。下列密码均已逐个与数据库BCrypt散列核验；未通过重置密码来推测或替换已有密码。

### 学生账号

| 用户ID | 登录账号 | 姓名 | 登录密码 | 联系电话 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 2 | 2023100513 | 田浩东 | Student123! | 13800138001 | 启用 |
| 3 | 2023100514 | 林沐言 | Student123! | — | 启用 |
| 4 | 2023100515 | 陈知远 | Student123! | — | 启用 |
| 5 | 2023100516 | 周雨桐 | Student123! | — | 启用 |
| 6 | 2023100517 | 许嘉宁 | Student123! | — | 启用 |
| 7 | 2023100518 | 何书涵 | Student123! | — | 启用 |
| 8 | 2023100519 | 吴星然 | Student123! | — | 启用 |
| 9 | 2023100520 | 赵一诺 | Student123! | — | 启用 |
| 10 | 2023100521 | 郑予安 | Student123! | — | 启用 |
| 11 | 2023100522 | 李清越 | Student123! | — | 启用 |
| 12 | 2023100523 | 王以宁 | Student123! | — | 启用 |
| 13 | 2023100524 | 黄景行 | Student123! | — | 启用 |
| 14 | 2023100525 | 刘若溪 | Student123! | — | 启用 |
| 15 | 2023100526 | 张明澈 | Student123! | — | 启用 |
| 16 | 2023100527 | 梁思齐 | Student123! | — | 启用 |
| 17 | 2023100528 | 谢云舒 | Student123! | — | 启用 |
| 18 | 2023100529 | 杨子衿 | Student123! | — | 启用 |
| 19 | 2023100530 | 孙亦舟 | Student123! | — | 启用 |
| 20 | 2023100531 | 宋安然 | Student123! | — | 启用 |
| 21 | 2023100532 | 邓映秋 | Student123! | — | 启用 |
| 22 | 2023100533 | 苏慕白 | Student123! | — | 启用 |
| 23 | 2023100534 | 冯初晴 | Student123! | — | 启用 |
| 24 | 2023100535 | 罗嘉树 | Student123! | — | 启用 |
| 25 | 2023100536 | 顾晚宁 | Student123! | — | 启用 |
| 26 | 2023100537 | 陆星河 | Student123! | — | 启用 |

### 管理员账号

| 用户ID | 登录账号 | 姓名 | 登录密码 | 联系电话 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | admin | 平台管理员 | Admin123! | — | 启用 |

### 房东账号

| 用户ID | 登录账号 | 姓名 | 登录密码 | 联系电话 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 28 | 13800138000 | 示例房东 | Landlord123! | 13800138000 | 启用 |
| 27 | 19912807032 | 林房东 | Landlord123! | — | 启用 |

### 房源与房东对应关系

每套房源的 `owner_id` 均指向 `LANDLORD` 账号。34套原先未归属的房源由其原联系人对应的 `13800138000` 账号管理；原房东 `19912807032` 的房源仍由本人管理。房源标题、价格、照片、设施、状态等原有信息不变，待审核房源仍需管理员审核后才公开。

| 房源ID | 房源名称 | 状态 | 月租（元） | 房东用户ID | 房东账号 | 房东姓名 | 房源联系电话 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 暨大旁的阳光一居 | 已上架 | 1800.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 2 | 江畔清爽两居室 | 已上架 | 2600.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 3 | 大学城静谧书香单间 | 已上架 | 980.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 4 | 地铁旁的温暖小家 | 已上架 | 2200.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 5 | 绿意环绕的宽敞三居 | 已上架 | 3600.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 6 | 老城里的明亮公寓 | 已上架 | 1650.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 7 | 图书馆旁的独立空间 | 已上架 | 1200.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 8 | 校园南门舒适两居 | 已上架 | 2400.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 9 | 城市天际线景观房 | 已上架 | 3200.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 10 | 待审核的房源示例 | 待审核 | 1500.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 11 | 暨南雅苑 · 南向采光一居 | 已上架 | 1680.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 12 | 华师书院 · 独立书房两居 | 已上架 | 2480.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 13 | 珠江悦居 · 清爽独卫单间 | 已上架 | 1080.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 14 | 赤岗晴庭 · 带阳台的舒适一居 | 已上架 | 1980.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 15 | 大学城书舍 · 通透大客厅三居 | 已上架 | 3680.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 16 | 南村松庭 · 近地铁的安静两居 | 已上架 | 2780.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 17 | 东山晴舍 · 南向采光一居 | 已上架 | 1760.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 18 | 越秀云庭 · 独立书房两居 | 已上架 | 2560.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 19 | 暨南雅苑 · 清爽独卫单间 2号房 | 已上架 | 1160.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 20 | 华师书院 · 带阳台的舒适一居 2号房 | 已上架 | 2060.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 21 | 珠江悦居 · 通透大客厅三居 2号房 | 已上架 | 3760.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 22 | 赤岗晴庭 · 近地铁的安静两居 2号房 | 已上架 | 2860.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 23 | 大学城书舍 · 南向采光一居 2号房 | 已上架 | 1840.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 24 | 南村松庭 · 独立书房两居 2号房 | 已上架 | 2640.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 25 | 东山晴舍 · 清爽独卫单间 2号房 | 已上架 | 1240.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 26 | 越秀云庭 · 带阳台的舒适一居 2号房 | 已上架 | 2140.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 27 | 暨南雅苑 · 通透大客厅三居 3号房 | 已上架 | 3840.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 28 | 华师书院 · 近地铁的安静两居 3号房 | 已上架 | 2940.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 29 | 珠江悦居 · 南向采光一居 3号房 | 已上架 | 1920.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 30 | 赤岗晴庭 · 独立书房两居 3号房 | 已上架 | 2720.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 31 | 大学城书舍 · 清爽独卫单间 3号房 | 已上架 | 1320.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 32 | 南村松庭 · 带阳台的舒适一居 3号房 | 已上架 | 2220.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 33 | 东山晴舍 · 通透大客厅三居 3号房 | 已上架 | 3920.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 34 | 越秀云庭 · 近地铁的安静两居 3号房 | 已上架 | 3020.0 | 28 | 13800138000 | 示例房东 | 13800138000 |
| 35 | 天河晴居 · 南向一居 | 已上架 | 2200.0 | 27 | 19912807032 | 林房东 | 13800138000 |

### 数据保留与业务起点

保留全部16张表及其结构。账号、房源及11条基础筛选字典保留；以下业务表记录均为0：

| 表 | 内容 | 记录数 |
| --- | --- | --- |
| `t_application` | 租赁申请 | 0 |
| `t_reservation` | 看房预约 | 0 |
| `t_contract` | 合同 | 0 |
| `t_moveout` | 退租申请 | 0 |
| `t_bill` | 账单 | 0 |
| `t_repair` | 报修工单 | 0 |
| `t_favorite` | 收藏 | 0 |
| `t_comment` | 评价 | 0 |
| `t_message` | 咨询留言 | 0 |
| `t_chat` | 聊天消息 | 0 |
| `t_media` | 聊天图片上传登记 | 0 |
| `t_notice` | 公告 | 0 |
| `t_audit` | 操作审计 | 0 |

清理使用带事务的DELETE，未删除表、未重置主键计数器。原账号字段及房源除 `owner_id` 外的字段逐项核验保持一致。清理前完整SQL与本机连接配置备份位于工作区私有目录 `.work/database-before-flow-reset-20261104/`，不进入源码分享包。核验摘要见 `docs/evidence/flow-reset.json`。

测试时先用房东账号查看“我的房源”，用学生账号从已上架房源申请租赁，再用房东审批；学生在“我的合同”、房东在“租赁合同”分别阅读并签署。之后可自行执行开单、报修、结清与退租。待审核房源可先由管理员审核。清单描述清理完成时的状态，后续登录、预约和签署会正常产生新的业务及审计记录。

## 角色与租赁流程

系统面向学生租客、房东和管理员。学生检索、收藏和预约房源，向房东申请租赁；房东维护本人房源、处理申请、合同、账单、报修与退租；管理员审核房源、学生认证和评价并维护平台数据。当前35套房源均关联有效房东账号；系统仍兼容历史托管房源记录。

房东通过申请时确认押金与补充约定，系统保存分条合同正文快照。双方分别查看全文，填写与个人资料一致的姓名、复核登录密码并勾选阅读与电子确认同意项。服务器校验归属、状态、同意项和正文SHA-256摘要，保存各方姓名、账号、服务器签署时间及摘要；双方均确认后状态为SIGNED。重复签署、非当事人操作、密码错误或正文摘要不一致均被拒绝。合同可打印并通过浏览器保存PDF。历史合同及原有签署记录保持原样，不补造姓名或时间。

平台提供账号电子确认记录，未提供实名认证、CA数字证书、可信时间戳和第三方存证，不承诺达到可靠电子签名标准。实际交易应核验身份、出租权和房屋情况，可另行选择纸质签约或依法提供认证服务的机构；法律效力需结合真实意思及证据依法判断。平台不提供在线支付，账单结清是房东登记线下收款。

聊天每2秒同步文字、图片、房源卡片与未读数；页面隐藏时暂停轮询。房东按合同开账单，派工登记维修费用及责任；退租审批前需结清账单并完成报修。房东通过注册创建账号。

## 功能

学生：注册登录与恢复、资料、学生认证、房源详情与组合搜索、分页排序、收藏、可用时段看房预约、取消待确认预约、在线咨询、模板与租赁意向、签约记录、评价及审核结果。

管理员：房源录入编辑审核上下架及逻辑删除、用户禁用解禁与学生材料审核、预约确认完成取消、合同签约与终止、咨询回复、评价审核、公告增改删、区域户型租金字典增改删、统计及图表、操作审计、本机在线备份。

关键规则：学生不发布房源；未审核房源不公开；修改后重审；同房同时段唯一占位；学生仅取消本人待确认预约；看房或签约后才能评价；评价先审后显；关联活跃预约或合同阻止删除；已有签约租期不能重叠；管理员操作也受状态机校验。

## 技术与目录

Spring Boot 3.5.7、Spring MVC、JdbcTemplate、HikariCP、BCrypt、H2/MySQL/MariaDB、原生HTML/CSS/JavaScript。内嵌Tomcat提供同源页面与JSON接口，不需要独立Node开发服务。

```text
src/main/java/com/campus/rental  控制器、服务、规则与数据访问
src/main/resources             数据库配置、表结构与本地页面资源
src/test/java                  JUnit规则、业务与性能测试
scripts                        启动、浏览器验收、备份恢复和只读诊断
config/application-local.example.properties  统一连接配置模板
docs/diagrams                   可编辑PlantUML图及渲染图片
docs/evidence                   页面截图、测试JSON、协作文本与恢复证据
docs/test-reports               项目测试与覆盖率归档
dist                           本机构建的JAR，源码分享包不包含
```

## Kali数据库

目标配置为 `192.168.93.128:3306/rental`，实际服务为 MariaDB 11.4.5，当前 mysql profile 通过兼容协议连接。已完成真实数据库上的14组浏览器流程和字典/时段接口验证。性能与100并发测量仍只代表隔离H2环境。

CMD和PowerShell入口统一读取 `config/application-local.properties`，无需维护第二份密码。首次从分享包启动时，从 `application-local.example.properties` 创建私有配置并填写密码。环境变量若定义同名参数，优先于文件。旧的 local.ps1/local.example.ps1 已移出项目归档；`-Profile local` 仍兼容映射为mysql。MariaDB profile也支持同一份私有properties配置。

SQL初始化不删除现有表，不负责迁移不兼容的历史结构，连接前应确认目标库内没有其他系统的同名 `t_` 表。推荐创建仅Windows主机可访问的项目专用账号。

## 界面与示例数据

学生端和管理端使用统一的中文界面，重点呈现搜索、房源照片、价格与配套信息。房源详情展示12项设施的已配备/未配备状态，以及楼层、朝向、装修、起租要求、付款、交通和入住日期；管理员表单可以维护这些字段。

本机连接Kali的配置及分享模板使用 `SEED_DEMO=false`、`SEED_CATALOG=false`，不会在启动时填充示例账号、房源或业务记录。当前数据库保留25个学生、1个管理员、2个房东账号及35套房源，具体账号、密码和房源归属见“全流程测试数据”章节。区域、户型与租金筛选字典作为基础配置保留。H2自动化测试仍在隔离库内自行初始化测试数据。

图片作者、来源和许可见 [图片来源与使用说明](docs/图片来源与使用说明.md)，设计依据见 [前端设计说明](docs/前端设计说明.md)。这些是网络空间配图，实际租赁应核验房屋与设备。

## 测试与证据

```powershell
mvn -B -ntp verify
```

<<<<<<< HEAD
自动化测试共75项，包含规则、业务、目录、性能及7项房东集成测试。签署用例验证同意项、姓名、密码、正文摘要、重复签署和数据归属。基线用例包括：35项规则用例、29项Web业务用例、3项示例目录用例及1项隔离性能测试。Surefire输出到 `target/surefire-reports`，JaCoCo覆盖率输出到 `target/site/jacoco`。报告中记录实际行与分支覆盖率；没有设定未经课程提供的“达标”阈值。
=======
本次68项自动化检查通过：35项规则用例、29项Web业务用例、3项示例目录用例及1项隔离性能测试。Surefire输出到 `target/surefire-reports`，JaCoCo覆盖率输出到 `target/site/jacoco`。
>>>>>>> b9a603270f46f6b13588a1f128e387e88e4abeb3

PerformanceTest在独立H2内存库创建10万条房源，以100个并发HTTP请求测量指定筛选查询，结果写入 `docs/evidence/performance.json`。不会污染演示库或远程数据库。该结果不包括完整浏览器加载、Kali网络、真实100人操作序列及长期可用性。

浏览器验收需要Node及Playwright，可按自己的环境安装；它们仅用于测试，运行项目不需要：

```powershell
npm install --no-save playwright
npx playwright install chromium
node scripts/ui-smoke.cjs
```

也可设置 `CHROME_PATH` 使用已安装Chrome，或设置 `PLAYWRIGHT_MODULE` 指向现有模块。脚本使用独立无头浏览器，检查14组页面流程并保存截图，不操作用户日常浏览器。后端服务需预先启动，默认BASE_URL为本机8080。

## 备份与恢复

demo运行时每天北京时间02:00创建数据库及上传图片备份，管理员也可从操作审计页面手动触发。备份位于 `data/backups`，未自动删除旧备份。恢复至新目录：

```powershell
./scripts/restore-demo.ps1 -Archive ./data/backups/备份文件.zip -OutputDirectory ./restored-demo
```

恢复脚本不覆盖现有目录，恢复后查询核心表计数。项目已完成真实隔离恢复验证，证据见 `docs/evidence/restore-verification.txt`。MySQL每日备份由Kali执行 `scripts/backup-mysql.sh`；详见维护文档。每日自动备份依赖应用或对应定时任务持续运行，不代表已测得99%可用性。

## 交付边界

平台提供账号电子确认记录，未提供实名认证、CA数字证书、可信时间戳和第三方存证，不承诺达到可靠电子签名标准。实际交易应核验身份、出租权和房屋情况，可另行选择纸质签约或依法提供认证服务的机构；法律效力需结合真实意思及证据依法判断。平台不提供在线支付，账单结清是房东登记线下收款。地图、短信与征信不在本期范围。8张室内摄影配图来自已核实的Unsplash免费页面，已压缩并本地保存；原矢量图仍保留供选择。示例地址与房源为虚构信息，不将网络照片称为对应地址的实拍。当前后台列表最多1000条，审计最多200条，规模增长时应加入后台分页与审计归档。安全控制用于本机教学项目，公开部署应重新进行HTTPS、凭据、访问范围和目标数据库验收。

## 参考与许可

框架选型参考 [Spring Boot官方文档](https://docs.spring.io/spring-boot/3.5/system-requirements.html) 和 [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) 的分层示例。没有将PetClinic或其他仓库业务代码复制到本工程。Spring、H2、MySQL驱动、MariaDB驱动及测试库等依赖各自保留其许可证，信息随Maven制品提供，运行JAR的嵌套依赖中保留原始META-INF。项目未默认替用户选择原创代码的公开授权协议。

<<<<<<< HEAD
课程实验中的协作在独立本地仓库真实模拟，含冲突、解决、合并和向本地裸仓库推送；bundle可复查，未向GitHub发布或创建Issue。需求变更、接口和缺陷说明均在docs中。

## 界面与合同依据

页面沿用青绿色、浅灰背景、白色内容区及侧栏导航。首页呈现找房、看房和签约流程指引，并提供可点击的看房与签约清单；房东工作台突出待办数据；合同以分条文书、双方签署区、状态及正文摘要呈现。小屏幕按单列排列，打印只输出完整合同及签署信息。

参考资料（查阅日期2026年11月4日）：国家市场监督管理总局《城镇房屋租赁合同（示范文本）》GF—2025—2614，https://htsfwb.samr.gov.cn/View?id=2340996b-882d-47a4-b74d-c30784628737；北京市人民政府门户网站《中华人民共和国电子签名法》（2019年修正），https://www.beijing.gov.cn/zhengce/zhengcefagui/202202/t20220208_2606143.html。合同为项目自编条款，参考示范文本事项结构，不标称官方原件。电子确认边界依据该法第三条、第十三条、第十四条与第十六条。

详细说明见 [合同与电子确认说明](docs/合同与电子确认说明.md)。

## 文件夹与文件职责

下表逐项列出源码、配置、脚本、设计与验收档案。按文件夹分组，文件路径相对于rental-hub。

### 项目根目录

| 文件 | 职责 |
| --- | --- |
| `.gitattributes` | 文本换行和文件属性规则 |
| `.gitignore` | 排除密码、运行数据和构建产物 |
| `DELIVERY-MANIFEST.sha256` | 源码分享包逐文件完整性清单 |
| `pom.xml` | Maven依赖、Java版本、打包与覆盖率配置 |
| `README.md` | 项目运行、业务边界和逐文件职责说明 |
| `启动项目.cmd` | 双击启动入口，调用PowerShell统一启动脚本 |

### config

| 文件 | 职责 |
| --- | --- |
| `application-local.example.properties` | 可分享的连接参数模板，不含真实密码 |

### docs/diagrams

| 文件 | 职责 |
| --- | --- |
| `01-student-usecases.png` | 对应系统设计图的渲染图片 |
| `01-student-usecases.puml` | 系统设计图的PlantUML可编辑源文件 |
| `02-admin-usecases.png` | 对应系统设计图的渲染图片 |
| `02-admin-usecases.puml` | 系统设计图的PlantUML可编辑源文件 |
| `03-architecture.png` | 对应系统设计图的渲染图片 |
| `03-architecture.puml` | 系统设计图的PlantUML可编辑源文件 |
| `04-class.png` | 对应系统设计图的渲染图片 |
| `04-class.puml` | 系统设计图的PlantUML可编辑源文件 |
| `05-reservation-sequence.png` | 对应系统设计图的渲染图片 |
| `05-reservation-sequence.puml` | 系统设计图的PlantUML可编辑源文件 |
| `06-er-complete.png` | 对应系统设计图的渲染图片 |
| `06-er-complete.puml` | 系统设计图的PlantUML可编辑源文件 |
| `06-er.png` | 对应系统设计图的渲染图片 |
| `06-er.puml` | 系统设计图的PlantUML可编辑源文件 |
| `07-reservation-state.png` | 对应系统设计图的渲染图片 |
| `07-reservation-state.puml` | 系统设计图的PlantUML可编辑源文件 |
| `08-reservation-flow.png` | 对应系统设计图的渲染图片 |
| `08-reservation-flow.puml` | 系统设计图的PlantUML可编辑源文件 |
| `11-landlord-usecases.png` | 对应系统设计图的渲染图片 |
| `11-landlord-usecases.puml` | 系统设计图的PlantUML可编辑源文件 |
| `12-landlord-er.png` | 对应系统设计图的渲染图片 |
| `12-landlord-er.puml` | 系统设计图的PlantUML可编辑源文件 |
| `13-application-sequence.png` | 对应系统设计图的渲染图片 |
| `13-application-sequence.puml` | 系统设计图的PlantUML可编辑源文件 |

### docs/evidence

| 文件 | 职责 |
| --- | --- |
| `flow-reset.json` | 全流程测试前数据清理与房源归属的只读核验摘要 |
| `01-home.png` | 浏览器页面验收截图 |
| `02-detail.png` | 浏览器页面验收截图 |
| `03-profile.png` | 浏览器页面验收截图 |
| `04-admin-stats.png` | 浏览器页面验收截图 |
| `05-admin-houses.png` | 浏览器页面验收截图 |
| `06-house-editor.png` | 浏览器页面验收截图 |
| `07-mobile.png` | 浏览器页面验收截图 |
| `08-facilities.png` | 浏览器页面验收截图 |
| `09-login.png` | 浏览器页面验收截图 |
| `10-mobile-detail.png` | 浏览器页面验收截图 |
| `backup-created.json` | 实际验收结果、诊断或构建证据记录 |
| `collaboration-lab.bundle` | 课程协作历史归档 |
| `delivery-validation.json` | 实际验收结果、诊断或构建证据记录 |
| `git-collaboration.txt` | 实际验收结果、诊断或构建证据记录 |
| `maven-verify.log` | 实际验收结果、诊断或构建证据记录 |
| `mysql-connection-check.txt` | 实际验收结果、诊断或构建证据记录 |
| `performance.json` | 实际验收结果、诊断或构建证据记录 |
| `project-history.bundle` | 课程协作历史归档 |
| `release-smoke.json` | 实际验收结果、诊断或构建证据记录 |
| `report-admin-houses.png` | 浏览器页面验收截图 |
| `report-build.json` | 实际验收结果、诊断或构建证据记录 |
| `report-detail.png` | 浏览器页面验收截图 |
| `report-home.png` | 浏览器页面验收截图 |
| `report-mobile.png` | 浏览器页面验收截图 |
| `report-profile.png` | 浏览器页面验收截图 |
| `report-stats.png` | 浏览器页面验收截图 |
| `responsive.json` | 实际验收结果、诊断或构建证据记录 |
| `restore-verification.txt` | 实际验收结果、诊断或构建证据记录 |
| `startup-check.json` | 实际验收结果、诊断或构建证据记录 |
| `supplementary.json` | 实际验收结果、诊断或构建证据记录 |
| `ui-results.json` | 实际验收结果、诊断或构建证据记录 |

### docs/evidence/landlord

| 文件 | 职责 |
| --- | --- |
| `01-house-pending.png` | 浏览器页面验收截图 |
| `02-application-approved.png` | 浏览器页面验收截图 |
| `03-contract.png` | 浏览器页面验收截图 |
| `04-bills.png` | 浏览器页面验收截图 |
| `05-repairs.png` | 浏览器页面验收截图 |
| `06-dashboard.png` | 浏览器页面验收截图 |
| `07-chat.png` | 浏览器页面验收截图 |
| `08-profile.png` | 浏览器页面验收截图 |
| `09-moveout.png` | 浏览器页面验收截图 |
| `10-mobile.png` | 浏览器页面验收截图 |
| `11-git-commits.png` | 本地版本控制历史图片，仅作原始档案，不在实验报告中引用 |
| `git-commands.txt` | 实际验收结果、诊断或构建证据记录 |
| `migration-verification.txt` | 实际验收结果、诊断或构建证据记录 |
| `results.json` | 实际验收结果、诊断或构建证据记录 |
| `validation.json` | 实际验收结果、诊断或构建证据记录 |

### docs

| 文件 | 职责 |
| --- | --- |
| `Kali部署与备份.md` | Kali部署与备份文档 |
| `前端设计说明.md` | 前端设计说明文档 |
| `协作规范.md` | 协作规范文档 |
| `合同与电子确认说明.md` | 合同与电子确认说明文档 |
| `图片来源与使用说明.md` | 图片来源与使用说明文档 |
| `实验报告正文.md` | 实验报告正文文档 |
| `房东角色需求与设计.md` | 房东角色需求与设计文档 |
| `数据库部署.sql` | 数据库部署建表脚本 |
| `缺陷跟踪.md` | 缺陷跟踪文档 |
| `网站修复与文件检查记录.md` | 网站修复与文件检查记录文档 |

### docs/test-reports/jacoco/com.campus.rental

| 文件 | 职责 |
| --- | --- |
| `AdminController.html` | 平台管理、审核、统计与字典接口 |
| `AdminController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `ApiErrors.html` | 统一返回参数、权限和数据库异常 |
| `ApiErrors.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `AuthController$Attempt.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `AuthController.html` | 注册、登录、验证码、密码恢复、个人资料与尝试次数限制 |
| `AuthController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `BackupService$BackupController.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `BackupService.html` | 数据库及上传文件备份 |
| `BackupService.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `Bootstrap.html` | 基础表数据和演示账号初始化 |
| `Bootstrap.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `HouseController.html` | 公开房源、字典、公告和合同模板查询 |
| `HouseController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `index.html` | 网站HTML入口，加载前端脚本与样式 |
| `index.source.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `LandlordMigration.html` | 兼容历史数据的房东字段与签署字段迁移 |
| `LandlordMigration.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `RentalApplication.html` | Spring Boot启动入口 |
| `RentalApplication.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `RentalController.html` | 房东租客申请、签署、账单、维修、退租及聊天接口 |
| `RentalController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `ReservationService.html` | 预约占位、并发冲突与状态流转 |
| `ReservationService.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `Rules.html` | 文本、密码、金额、日期与状态规则 |
| `Rules.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `SampleCatalog.html` | 示例房源资料及字段初始化 |
| `SampleCatalog.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `Store.html` | 参数化SQL、数据库访问和操作审计 |
| `Store.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `StudentController.html` | 学生收藏、认证、意向、预约与评价接口 |
| `StudentController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `UploadController.html` | 图片验证、重编码与受控访问 |
| `UploadController.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `WebConfig$1.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `WebConfig.html` | 会话权限、CSRF和Web安全响应配置 |
| `WebConfig.java.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |

### docs/test-reports/jacoco

| 文件 | 职责 |
| --- | --- |
| `index.html` | 网站HTML入口，加载前端脚本与样式 |
| `jacoco-sessions.html` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `jacoco.csv` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |
| `jacoco.xml` | JaCoCo对应类或整体覆盖率报告；由verify生成归档 |

### docs/test-reports/jacoco/jacoco-resources

| 文件 | 职责 |
| --- | --- |
| `branchfc.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `branchnc.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `branchpc.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `bundle.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `class.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `down.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `greenbar.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `group.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `method.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `package.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `prettify.css` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `prettify.js` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `redbar.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `report.css` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `report.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `session.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `sort.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `sort.js` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `source.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |
| `up.gif` | JaCoCo覆盖率网页使用的图标、样式或脚本 |

### docs/test-reports/surefire

| 文件 | 职责 |
| --- | --- |
| `2026-10-24T13-41-39_802.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-42-53_902.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-43-25_624.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-45-51_361.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-48-54_049.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-50-28_676.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-55-28_757.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T13-56-37_052.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T14-03-47_280.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T14-22-43_983.dumpstream` | 对应测试类的Surefire结果归档 |
| `2026-10-24T19-09-24_349.dumpstream` | 对应测试类的Surefire结果归档 |
| `com.campus.rental.LandlordTest.txt` | 对应测试类的Surefire结果归档 |
| `com.campus.rental.PerformanceTest.txt` | 对应测试类的Surefire结果归档 |
| `com.campus.rental.RulesTest.txt` | 对应测试类的Surefire结果归档 |
| `com.campus.rental.SampleCatalogTest.txt` | 对应测试类的Surefire结果归档 |
| `com.campus.rental.WorkflowTest.txt` | 对应测试类的Surefire结果归档 |
| `TEST-com.campus.rental.LandlordTest.xml` | 对应测试类的Surefire结果归档 |
| `TEST-com.campus.rental.PerformanceTest.xml` | 对应测试类的Surefire结果归档 |
| `TEST-com.campus.rental.RulesTest.xml` | 对应测试类的Surefire结果归档 |
| `TEST-com.campus.rental.SampleCatalogTest.xml` | 对应测试类的Surefire结果归档 |
| `TEST-com.campus.rental.WorkflowTest.xml` | 对应测试类的Surefire结果归档 |

### scripts

| 文件 | 职责 |
| --- | --- |
| `backup-mysql.sh` | Kali端数据库备份命令 |
| `DbCheck.java` | 只读数据库连接诊断 |
| `design-check.cjs` | 页面布局、窄屏和截图检查 |
| `landlord-smoke.cjs` | 房东租客完整流程与签署浏览器验收 |
| `restore-demo.ps1` | 将H2备份恢复到独立目录 |
| `start.ps1` | 构建、模式选择、端口检测和浏览器打开 |
| `supplementary-smoke.ps1` | 补充接口与服务检查 |
| `ui-smoke.cjs` | 学生和管理端浏览器验收 |

### src/main/java/com/campus/rental

| 文件 | 职责 |
| --- | --- |
| `AdminController.java` | 平台管理、审核、统计与字典接口 |
| `ApiErrors.java` | 统一返回参数、权限和数据库异常 |
| `AuthController.java` | 注册、登录、验证码、密码恢复、个人资料与尝试次数限制 |
| `BackupService.java` | 数据库及上传文件备份 |
| `Bootstrap.java` | 基础表数据和演示账号初始化 |
| `ContractText.java` | 分条合同正文和SHA-256摘要 |
| `HouseController.java` | 公开房源、字典、公告和合同模板查询 |
| `LandlordMigration.java` | 兼容历史数据的房东字段与签署字段迁移 |
| `RentalApplication.java` | Spring Boot启动入口 |
| `RentalController.java` | 房东租客申请、签署、账单、维修、退租及聊天接口 |
| `ReservationService.java` | 预约占位、并发冲突与状态流转 |
| `Rules.java` | 文本、密码、金额、日期与状态规则 |
| `SampleCatalog.java` | 示例房源资料及字段初始化 |
| `Store.java` | 参数化SQL、数据库访问和操作审计 |
| `StudentController.java` | 学生收藏、认证、意向、预约与评价接口 |
| `UploadController.java` | 图片验证、重编码与受控访问 |
| `WebConfig.java` | 会话权限、CSRF和Web安全响应配置 |

### src/main/resources

| 文件 | 职责 |
| --- | --- |
| `application-demo.properties` | 隔离H2演示配置 |
| `application-mariadb.properties` | MariaDB连接配置 |
| `application-mysql.properties` | MySQL连接配置 |
| `application.properties` | 公共运行参数及默认配置 |
| `schema.sql` | 业务表、约束及索引定义 |

### src/main/resources/static

| 文件 | 职责 |
| --- | --- |
| `app.css` | 共享配色、页面布局、响应式与合同打印样式 |
| `app.js` | 页面路由、房源检索、详情及学生和管理员交互 |
| `favicon.svg` | 网站与默认头像图标 |
| `index.html` | 网站HTML入口，加载前端脚本与样式 |
| `rental.js` | 房东租客页面、合同文书签署、聊天及业务交互 |

### src/main/resources/static/images

| 文件 | 职责 |
| --- | --- |
| `home-1.jpg` | 房源室内参考摄影配图 |
| `home-2.jpg` | 房源室内参考摄影配图 |
| `home-3.jpg` | 房源室内参考摄影配图 |
| `home-4.jpg` | 房源室内参考摄影配图 |
| `home-5.jpg` | 房源室内参考摄影配图 |
| `home-6.jpg` | 房源室内参考摄影配图 |
| `home-7.jpg` | 房源室内参考摄影配图 |
| `home-8.jpg` | 房源室内参考摄影配图 |
| `photo-credits.json` | 室内照片作者、来源及许可元数据 |
| `room-1.svg` | 房源矢量参考配图 |
| `room-2.svg` | 房源矢量参考配图 |
| `room-3.svg` | 房源矢量参考配图 |
| `room-4.svg` | 房源矢量参考配图 |
| `room-5.svg` | 房源矢量参考配图 |
| `room-6.svg` | 房源矢量参考配图 |

### src/test/java/com/campus/rental

| 文件 | 职责 |
| --- | --- |
| `LandlordTest.java` | 三角色隔离与租约生命周期和签署校验测试 |
| `PerformanceTest.java` | 隔离H2十万条数据和100并发查询测量 |
| `RulesTest.java` | 基础规则边界与参数化测试 |
| `SampleCatalogTest.java` | 示例目录初始化与幂等测试 |
| `WorkflowTest.java` | 原业务权限、事务与状态集成测试 |

### 本地配置与运行目录

| 文件夹或文件 | 职责 |
| --- | --- |
| `config/application-local.properties` | 本机数据库私有凭据，分享包排除 |
| `.git/` | 本地版本对象、分支和索引，由Git管理 |
| `dist/*.jar` | 本机启动使用的可运行JAR |
| `target/classes/`、`target/test-classes/` | Maven编译结果 |
| `target/surefire-reports/`、`target/site/jacoco/` | 最近一次测试及覆盖率 |
| `target/*.jar`、`target/maven-*/` | 构建包与Maven中间元数据 |
| `target/*-uploads/`、`target/*-backups/` | 隔离测试创建的上传与备份数据 |
| `data/*.mv.db`、`data/*.trace.db` | H2演示数据库及诊断文件 |
| `data/uploads/` | 上传图片及受控认证材料，内容按用户上传生成 |
| `data/backups/` | 演示数据库和上传文件备份压缩包 |
| `logs/` | 本机服务运行日志及启动诊断 |

运行目录内容随启动、测试和上传变化，不作为源码文件交付。

### docs/evidence/contract-ux

2026年11月4日的隔离H2检查档案，保留原Kali验证档案。

| 文件 | 职责 |
| --- | --- |
| `01-house-pending.png` | 桌面或移动端页面检查截图 |
| `02-application-approved.png` | 桌面或移动端页面检查截图 |
| `03-contract.png` | 桌面或移动端页面检查截图 |
| `04-bills.png` | 桌面或移动端页面检查截图 |
| `05-repairs.png` | 桌面或移动端页面检查截图 |
| `06-dashboard.png` | 桌面或移动端页面检查截图 |
| `07-chat.png` | 桌面或移动端页面检查截图 |
| `09-moveout.png` | 桌面或移动端页面检查截图 |
| `10-mobile-detail.png` | 桌面或移动端页面检查截图 |
| `10-mobile.png` | 桌面或移动端页面检查截图 |
| `browser.txt` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `contract-print.pdf` | 完整合同及双方签署信息的浏览器打印结果 |
| `contract-reader.png` | 桌面或移动端页面检查截图 |
| `contract-signatures.png` | 桌面或移动端页面检查截图 |
| `design.txt` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `home-desktop.png` | 桌面或移动端页面检查截图 |
| `home-mobile.png` | 桌面或移动端页面检查截图 |
| `performance.json` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `responsive.json` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `results.json` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `validation.json` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `verify.txt` | 自动化测试、浏览器流程、响应式或打印结果记录 |
| `visual.txt` | 自动化测试、浏览器流程、响应式或打印结果记录 |

设置`EVIDENCE_DIR`可将房东浏览器脚本输出到独立档案目录；测试服务应使用隔离数据库。当前验证：75项自动化测试、9组房东业务流程及320至1440像素页面检查通过；合同打印包含完整正文和签署记录。

学生从“我的合同”查看所有本人合同，房东合同可在同一入口阅读全文并签署；托管房源意向与历史原文有明确标注。“查看合同模板”展示分条参考文本及打印入口。托管房源的意向草案带入房屋、租客和租期信息，出租人尚未参与时不提供代签。
=======
课程实验中的协作在独立本地仓库真实模拟，含冲突、解决、合并和向本地裸仓库推送；bundle可复查。需求变更、接口和缺陷说明均在docs中。
>>>>>>> b9a603270f46f6b13588a1f128e387e88e4abeb3
