# 校园安居 租房信息管理系统

基于随附需求规格说明书实现的Java 21课程项目，覆盖学生与管理员两类角色、FR-01至FR-17。Web应用在Windows本机运行，默认通过JDBC连接Kali的MySQL数据库。Navicat仅用于管理同一数据库，不需要启动单独的本机MySQL。H2保留为显式选择的离线验证模式。业务代码为本项目原创实现，未复制第三方租房项目。

## 快速运行

确保安装JDK 21，双击 **启动项目.cmd**。本机私有连接配置已经填写；分享包首次启动会创建并打开 `config/application-local.properties`，填写数据库密码后重新启动。目标为 `192.168.93.128:3306/kali`，用户名 `root`。启动成功后访问 [校园安居](http://127.0.0.1:8080)。随交付包提供 `dist/rental-hub-1.0.0.jar`，无需重新下载依赖即可运行。

| 角色 | 演示账号 | 演示密码 |
| --- | --- | --- |
| 学生 | 2023100513 | Student123! |
| 管理员 | admin | Admin123! |

演示验证码为本机应用生成的简单加法题。注册创建学生账号，并只展示一次密码恢复密钥，请自行保存。演示学生恢复密钥为 `demo-recovery-2026`。以上账号在 `SEED_DEMO=true` 且首次初始化时创建，不会显示在成品网站上。已有数据库不会覆盖房源或重建账号。

从源码运行：

```powershell
mvn -B -ntp verify
java -Dfile.encoding=UTF-8 -jar target/rental-hub-1.0.0.jar
```

也可运行 `./scripts/start.ps1`，默认加载本机私有远程连接配置。Kali暂时不可用时，可显式双击 **启动演示.cmd** 或运行 `./scripts/start.ps1 -Profile demo`，使用独立H2文件库验证页面；常规启动不会自动回退数据库。PowerShell脚本若受本机执行策略限制，可使用CMD启动脚本或直接运行Java命令，无需更改全局系统策略。

默认监听127.0.0.1:8080，按Ctrl+C停止。端口占用时设置 `PORT=8081` 后使用Java启动，或运行 `./scripts/start.ps1 -Profile demo -Port 8081`。MySQL业务数据保存在Kali，上传文件位于Windows项目 `data/uploads`，日志位于 `logs`；H2模式的数据库位于 `data`。再次启动保留已有数据，不重复覆盖示例房源。

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
config/local.example.ps1        可复制的远程数据库连接示例
docs/diagrams                   可编辑PlantUML图及渲染图片
docs/evidence                   实际截图、测试JSON、Git模拟与恢复证据
docs/test-reports               本次测试与覆盖率归档
dist                           本机可直接运行的JAR
```

## Kali数据库

目标配置是 `192.168.93.128:3306/kali`。本次再次只读检查仍未收到数据库响应，按用户要求暂缓联调；连接失败时常规启动会报错退出，不会改用其他数据库。MySQL运行与性能尚未验收，不用H2结果替代。完整步骤见 [Kali部署与备份](docs/Kali部署与备份.md)。

CMD及直接Java启动读取 `config/application-local.properties`；可复制同目录 `.example.properties` 填写。PowerShell入口读取 `config/local.ps1`，可从 `local.example.ps1` 复制填写，执行 `./scripts/start.ps1 -Profile local`。环境变量优先于properties；使用其中一种入口即可，修改连接时同步维护实际使用的配置。本机已填写的私有local配置不进入Git或可分享压缩包。`SEED_DEMO=false` 时不创建固定学生和示例房源；首次无管理员时通过 `ADMIN_PASSWORD` 设置密码。Kali实际使用MariaDB时改用mariadb profile。

SQL初始化不删除现有表，不负责迁移不兼容的历史结构，连接前应确认目标库内没有其他系统的同名 `t_` 表。推荐创建仅Windows主机可访问的项目专用账号。

## 测试与证据

```powershell
mvn -B -ntp verify
```

本次64项自动化检查通过：35项规则用例、28项Web业务用例及1项隔离性能测试。Surefire输出到 `target/surefire-reports`，JaCoCo覆盖率输出到 `target/site/jacoco`。报告中记录实际行与分支覆盖率；没有设定未经课程提供的“达标”阈值。

PerformanceTest在独立H2内存库创建10万条房源，以100个并发HTTP请求测量指定筛选查询，结果写入 `docs/evidence/performance.json`。不会污染演示库或远程数据库。该结果不包括完整浏览器加载、Kali网络、真实100人操作序列及长期可用性。

浏览器验收需要Node及Playwright，可按自己的环境安装；它们仅用于测试，运行项目不需要：

```powershell
npm install --no-save playwright
npx playwright install chromium
node scripts/ui-smoke.cjs
```

也可设置 `CHROME_PATH` 使用已安装Chrome，或设置 `PLAYWRIGHT_MODULE` 指向现有模块。脚本使用独立无头浏览器，检查10组页面流程并保存截图，不操作用户日常浏览器。后端服务需预先启动，默认BASE_URL为本机8080。

## 备份与恢复

demo运行时每天北京时间02:00创建数据库及上传图片备份，管理员也可从操作审计页面手动触发。备份位于 `data/backups`，未自动删除旧备份。恢复至新目录：

```powershell
./scripts/restore-demo.ps1 -Archive ./data/backups/备份文件.zip -OutputDirectory ./restored-demo
```

恢复脚本不覆盖现有目录，恢复后查询核心表计数。本次已完成真实隔离恢复验证，证据见 `docs/evidence/restore-verification.txt`。MySQL每日备份由Kali执行 `scripts/backup-mysql.sh`；详见维护文档。每日自动备份依赖应用或对应定时任务持续运行，不代表已测得99%可用性。

## 交付边界

合同为租赁意向和线下签约记录，没有在线支付或电子签章。房东、地图、短信与征信不在本期范围。演示图像是原创本地矢量插图，地址与房源为虚构课程数据。当前后台列表最多1000条，审计最多200条，规模增长时应加入后台分页与审计归档。安全控制用于本机教学项目，公开部署应重新进行HTTPS、凭据、访问范围和目标数据库验收。

## 参考与许可

框架选型参考 [Spring Boot官方文档](https://docs.spring.io/spring-boot/3.5/system-requirements.html) 和 [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) 的分层示例。没有将PetClinic或其他仓库业务代码复制到本工程。Spring、H2、MySQL驱动、MariaDB驱动及测试库等依赖各自保留其许可证，信息随Maven制品提供，运行JAR的嵌套依赖中保留原始META-INF。项目未默认替用户选择原创代码的公开授权协议。

课程实验中的协作在独立本地仓库真实模拟，含冲突、解决、合并和向本地裸仓库推送；bundle可复查，未向GitHub发布或创建Issue。需求变更、接口和缺陷说明均在docs中。
