package com.campus.rental;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(10)
public class Bootstrap implements CommandLineRunner {
  private final Store db;
  private final boolean demo;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public Bootstrap(Store db, @Value("${rental.seed-demo:false}") boolean demo) {
    this.db = db;
    this.demo = demo;
  }

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    ensureIndex("t_house", "idx_house_search", "status,region,layout,rent,id");
    ensureIndex("t_reservation", "idx_reserve_user", "user_id,status");
    ensureIndex("t_contract", "idx_contract_house", "house_id,status,start_date,end_date");
    if (db.count("SELECT COUNT(*) FROM t_dictionary") == 0) {
      for (String label : List.of("天河区", "海珠区", "番禺区", "越秀区"))
        db.update("INSERT INTO t_dictionary(category,label) VALUES ('REGION',?)", label);
      for (String label : List.of("一室一厅", "两室一厅", "三室两厅", "单间"))
        db.update("INSERT INTO t_dictionary(category,label) VALUES ('LAYOUT',?)", label);
      db.update(
          "INSERT INTO t_dictionary(category,label,min_value,max_value) VALUES"
              + " ('RENT','1500元以下',0,1500),('RENT','1500至2500元',1500,2500),('RENT','2500至5000元',2500,5000)");
    }
    if (db.count("SELECT COUNT(*) FROM t_user WHERE role='ADMIN'") == 0) {
      String password = demo ? "Admin123!" : System.getenv("ADMIN_PASSWORD");
      if (password == null || password.length() < 8)
        throw new IllegalStateException("首次启动请设置 ADMIN_PASSWORD（至少8位，包含字母数字）");
      Rules.password(Map.of("password", password));
      db.update(
          "INSERT INTO t_user(account,password_hash,recovery_hash,name,role) VALUES"
              + " ('admin',?,?,?,'ADMIN')",
          encoder.encode(password),
          encoder.encode(UUID.randomUUID().toString()),
          "平台管理员");
    }
    if (demo) refreshSeedLabels();
    if (!demo || db.count("SELECT COUNT(*) FROM t_house") > 0) return;
    long admin =
        ((Number) db.one("SELECT id FROM t_user WHERE role='ADMIN' ORDER BY id LIMIT 1").get("id"))
            .longValue();
    long student;
    var students = db.list("SELECT id FROM t_user WHERE account='2023100513'");
    if (students.isEmpty())
      student =
          db.insert(
              "INSERT INTO t_user(account,password_hash,recovery_hash,name,school,phone) VALUES"
                  + " (?,?,?,?,?,?)",
              "2023100513",
              encoder.encode("Student123!"),
              encoder.encode("demo-recovery-2026"),
              "田浩东",
              "暨南大学",
              "13800138001");
    else student = Store.id(students.getFirst());
    String[] titles = {
      "暨大旁的阳光一居",
      "江畔清爽两居室",
      "大学城静谧书香单间",
      "地铁旁的温暖小家",
      "绿意环绕的宽敞三居",
      "老城里的明亮公寓",
      "图书馆旁的独立空间",
      "校园南门舒适两居",
      "城市天际线景观房",
      "待审核的房源示例"
    };
    String[] regions = {"天河区", "海珠区", "番禺区", "天河区", "番禺区", "越秀区", "天河区", "番禺区", "海珠区", "天河区"};
    String[] layouts = {"一室一厅", "两室一厅", "单间", "一室一厅", "三室两厅", "一室一厅", "单间", "两室一厅", "两室一厅", "单间"};
    int[] rents = {1800, 2600, 980, 2200, 3600, 1650, 1200, 2400, 3200, 1500},
        areas = {42, 68, 22, 38, 98, 35, 26, 62, 76, 28};
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < titles.length; i++) {
      long id =
          db.insert(
              "INSERT INTO"
                  + " t_house(title,region,address,rent,area,layout,facilities,description,image,owner_name,owner_phone,status,reviewer_id,reviewed_at,review_note)"
                  + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
              titles[i],
              regions[i],
              regions[i] + "大学路" + (i + 1) + "号（示例地址）",
              rents[i],
              areas[i],
              layouts[i],
              "空调 / 热水器 / 洗衣机 / 宽带 / 独立卫浴",
              "采光通透，生活设施齐全，适合学生学习和生活。附近有公交、便利店与生活市场。此记录为示例房源，配图仅供参考，实际租赁前请核验房源。",
              "/images/room-" + (i % 6 + 1) + ".svg",
              "示例房东",
              "13800138000",
              i == 9 ? "PENDING" : "PUBLISHED",
              i == 9 ? null : admin,
              "示例房源已标注");
      ids.add(id);
    }
    db.update("INSERT INTO t_favorite(user_id,house_id) VALUES (?,?)", student, ids.getFirst());
    db.update(
        "INSERT INTO t_reservation(user_id,house_id,visit_time,phone,status,active_slot) VALUES"
            + " (?,?,?,?,'COMPLETED',?)",
        student,
        ids.get(1),
        LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
        "13800138001",
        "demo-completed-slot");
    db.update(
        "INSERT INTO t_message(user_id,house_id,content,reply,replied_by,replied_at) VALUES"
            + " (?,?,?,?,?,CURRENT_TIMESTAMP)",
        student,
        ids.getFirst(),
        "请问可以预约周末看房吗？",
        "可以，请在房源详情中选择未来30天内的整点时段。",
        admin);
    db.update(
        "INSERT INTO t_notice(title,content,publisher_id) VALUES (?,?,?)",
        "欢迎使用校园安居",
        "房源先审后发。请在看房时核验身份与产权，勿向陌生账号预付费用。示例房源信息仅供参考，请以实地核验为准。",
        admin);
    db.audit(admin, "DEMO_SEED", "SYSTEM", 1, "初始化示例数据");
  }

  private void refreshSeedLabels() {
    db.update(
        "UPDATE t_house SET description=? WHERE description=?",
        "采光通透，生活设施齐全，适合学生学习和生活。附近有公交、便利店与生活市场。此记录为示例房源，配图仅供参考，实际租赁前请核验房源。",
        "采光通透，生活设施齐全，适合学生学习和生活。附近有公交、便利店与生活市场。此记录为课程演示数据，插图非房屋实拍，实际租赁前请核验房源。");
    db.update("UPDATE t_house SET review_note=? WHERE review_note=?", "示例房源已标注", "课程演示数据已标注");
    db.update(
        "UPDATE t_notice SET content=? WHERE title=? AND content=?",
        "房源先审后发。请在看房时核验身份与产权，勿向陌生账号预付费用。示例房源信息仅供参考，请以实地核验为准。",
        "欢迎使用校园安居",
        "房源先审后发。请在看房时核验身份与产权，勿向陌生账号预付费用。当前房源为课程演示数据。");
    db.update(
        "UPDATE t_audit SET detail=? WHERE action='DEMO_SEED' AND detail=?",
        "初始化示例数据",
        "初始化课程演示数据");
  }

  private void ensureIndex(String table, String index, String columns) throws SQLException {
    boolean found = false;
    try (var connection = db.dataSource().getConnection();
        var rows =
            connection
                .getMetaData()
                .getIndexInfo(connection.getCatalog(), null, table, false, false)) {
      while (rows.next()) if (index.equalsIgnoreCase(rows.getString("INDEX_NAME"))) found = true;
    }
    if (!found) db.update("CREATE INDEX " + index + " ON " + table + "(" + columns + ")");
  }
}
