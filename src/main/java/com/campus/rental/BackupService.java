package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.zip.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/** 演示数据库在线一致性快照；远程MySQL使用交付的mysqldump脚本。 */
@Service
@EnableScheduling
public class BackupService {
  private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
  private final Store db;
  private final Path uploads;

  public BackupService(Store db, @Value("${rental.upload-dir}") String uploads) {
    this.db = db;
    this.uploads = Path.of(uploads).toAbsolutePath().normalize();
  }

  public synchronized String create() throws Exception {
    try (var c = db.dataSource().getConnection()) {
      require(
          c.getMetaData().getDatabaseProductName().equals("H2"),
          "MySQL请在Kali运行 scripts/backup-mysql.sh 完成每日备份");
    }
    Path dir = Path.of("data/backups").toAbsolutePath().normalize();
    Files.createDirectories(dir);
    String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
    Path database = dir.resolve("database-" + suffix + ".zip"),
        archive = dir.resolve("rental-backup-" + suffix + ".zip");
    db.update("BACKUP TO '" + database.toString().replace("'", "''") + "'");
    try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
      out.putNextEntry(new ZipEntry("database.zip"));
      Files.copy(database, out);
      out.closeEntry();
      if (Files.exists(uploads))
        try (var paths = Files.walk(uploads)) {
          for (Path file : paths.filter(Files::isRegularFile).toList()) {
            out.putNextEntry(
                new ZipEntry("uploads/" + uploads.relativize(file).toString().replace('\\', '/')));
            Files.copy(file, out);
            out.closeEntry();
          }
        }
    } finally {
      Files.deleteIfExists(database);
    }
    return archive.toString();
  }

  @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai")
  public void daily() {
    try (var c = db.dataSource().getConnection()) {
      if (c.getMetaData().getDatabaseProductName().equals("H2")) {
        create();
        LOG.info("每日本机数据备份完成");
      }
    } catch (Exception e) {
      LOG.error("每日备份失败，类型={}", e.getClass().getSimpleName());
    }
  }

  @RestController
  @RequestMapping("/api/admin/backups")
  public static class BackupController {
    private final BackupService backup;
    private final Store db;

    public BackupController(BackupService backup, Store db) {
      this.backup = backup;
      this.db = db;
    }

    @PostMapping
    public Map<String, Object> create(HttpSession s) throws Exception {
      String file = backup.create();
      db.audit(WebConfig.uid(s), "BACKUP_CREATE", "SYSTEM", 1, "创建数据库及上传文件备份");
      return Map.of(
          "message", "备份完成，文件位于项目 data/backups 目录", "file", Path.of(file).getFileName().toString());
    }
  }
}
