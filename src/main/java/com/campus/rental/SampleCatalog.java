package com.campus.rental;

import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 可选示例目录扩充；仅迁移缺失列，新增数据由持久化标记保证只执行一次。 */
@Component
@Order(20)
public class SampleCatalog implements CommandLineRunner {
  private final Store db;
  private final boolean enabled;

  public SampleCatalog(Store db, @Value("${rental.catalog-samples:false}") boolean enabled) {
    this.db = db;
    this.enabled = enabled;
  }

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    migrateColumns();
    if (!enabled) return;
    if (db.count("SELECT COUNT(*) FROM t_audit WHERE action='CATALOG_SAMPLE_INIT'") > 0) return;
    long admin = Store.id(db.one("SELECT id FROM t_user WHERE role='ADMIN' ORDER BY id LIMIT 1"));
    var encoder = new BCryptPasswordEncoder();
    String[] names = {
      "林沐言", "陈知远", "周雨桐", "许嘉宁", "何书涵", "吴星然", "赵一诺", "郑予安", "李清越", "王以宁", "黄景行", "刘若溪", "张明澈",
      "梁思齐", "谢云舒", "杨子衿", "孙亦舟", "宋安然", "邓映秋", "苏慕白", "冯初晴", "罗嘉树", "顾晚宁", "陆星河"
    };
    for (int i = 0; i < names.length; i++) {
      String account = Long.toString(2023100514L + i);
      if (db.count("SELECT COUNT(*) FROM t_user WHERE account=?", account) == 0)
        db.update(
            "INSERT INTO t_user(account,password_hash,recovery_hash,name,school,phone)"
                + " VALUES(?,?,?,?,?,'')",
            account,
            encoder.encode("Student123!"),
            encoder.encode(UUID.randomUUID().toString()),
            names[i],
            "暨南大学");
    }
    String[] areas = {"天河区", "海珠区", "番禺区", "越秀区"};
    String[] estates = {"暨南雅苑", "华师书院", "珠江悦居", "赤岗晴庭", "大学城书舍", "南村松庭", "东山晴舍", "越秀云庭"};
    String[] types = {"一室一厅", "两室一厅", "单间", "一室一厅", "三室两厅", "两室一厅"};
    String[] highlights = {"南向采光一居", "独立书房两居", "清爽独卫单间", "带阳台的舒适一居", "通透大客厅三居", "近地铁的安静两居"};
    String[] transport = {
      "地铁3号线华师站，步行约8分钟", "地铁8号线客村站，步行约10分钟", "地铁4号线大学城北站，步行约12分钟", "地铁1号线东山口站，步行约9分钟"
    };
    String[] sets = {
      "冰箱 / 洗衣机 / 热水器 / 空调 / 床 / 衣柜 / 书桌 / 宽带 / 独立卫浴 / 厨房 / 阳台 / 电梯",
      "洗衣机 / 热水器 / 空调 / 床 / 衣柜 / 书桌 / 宽带 / 独立卫浴 / 厨房",
      "冰箱 / 热水器 / 空调 / 床 / 衣柜 / 宽带 / 独立卫浴 / 阳台",
      "冰箱 / 洗衣机 / 热水器 / 空调 / 床 / 衣柜 / 书桌 / 宽带 / 厨房 / 电梯"
    };
    for (int i = 0; i < 24; i++) {
      String title =
          estates[i % 8] + " · " + highlights[i % 6] + (i >= 8 ? " " + (i / 8 + 1) + "号房" : "");
      if (db.count("SELECT COUNT(*) FROM t_house WHERE title=?", title) > 0) continue;
      int region = (i % 8) / 2;
      int rent = new int[] {1680, 2480, 1080, 1980, 3680, 2780}[i % 6] + (i / 6) * 80;
      int area = new int[] {39, 66, 24, 43, 96, 71}[i % 6];
      db.update(
          "INSERT INTO"
              + " t_house(title,region,address,rent,area,layout,facilities,description,image,owner_name,owner_phone,status,reviewer_id,reviewed_at,review_note,floor_info,orientation,decoration,lease_term,payment_method,transport,available_from)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED',?,CURRENT_TIMESTAMP,?,?,?,?,?,?,?,?)",
          title,
          areas[region],
          areas[region] + estates[i % 8] + "校园路" + (20 + i) + "号（示例地址）",
          rent,
          area,
          types[i % 6],
          sets[i % 4],
          "空间方正，动静分区清楚，设有日常学习与收纳空间。"
              + (i % 2 == 0 ? "客厅与卧室采光舒适，阳台可用于晾晒。" : "卧室布局紧凑，适合重视独立生活空间的同学。")
              + "\n周边生活设施与交通信息供找房比较参考。\n此记录为示例房源，网络配图不对应具体地址，设备、费用和距离请以实地看房及书面约定为准。",
          "/images/home-" + (i % 8 + 1) + ".jpg",
          "示例房东",
          "13800138000",
          admin,
          "示例信息已核对，配图仅供参考",
          (i % (i % 2 == 0 ? 16 : 10) + 2) + "/" + (i % 2 == 0 ? 18 : 12) + "层",
          new String[] {"南", "南北", "东南", "东"}[i % 4],
          i % 3 == 0 ? "精装修" : "标准装修",
          i % 3 == 0 ? "半年起租" : "一年起租",
          i % 2 == 0 ? "押一付一" : "押一付三",
          transport[region],
          LocalDate.now().plusDays(i % 8));
    }
    for (var house :
        db.list(
            "SELECT id,image FROM t_house WHERE owner_name='示例房东' AND image LIKE"
                + " '/images/room-%.svg' ORDER BY id")) {
      long id = Store.id(house);
      int i = (int) ((id - 1) % 8);
      db.update(
          "UPDATE t_house SET"
              + " image=?,floor_info=?,orientation=?,decoration=?,lease_term=?,payment_method=?,transport=?,available_from=?"
              + " WHERE id=? AND image=?",
          "/images/home-" + (i + 1) + ".jpg",
          (i + 3) + "/18层",
          i % 2 == 0 ? "南" : "南北",
          "标准装修",
          "一年起租",
          "押一付一",
          transport[i % 4],
          LocalDate.now().plusDays(i % 4),
          id,
          house.get("image"));
    }
    db.audit(admin, "CATALOG_SAMPLE_INIT", "SYSTEM", 1, "新增24个示例学生及24套示例房源，补充配图与房源参数");
  }

  private void migrateColumns() throws Exception {
    Set<String> existing = new HashSet<>();
    try (var c = db.dataSource().getConnection();
        var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM t_house WHERE 1=0")) {
      var m = r.getMetaData();
      for (int i = 1; i <= m.getColumnCount(); i++)
        existing.add(m.getColumnName(i).toLowerCase(Locale.ROOT));
    }
    Map<String, String> columns = new LinkedHashMap<>();
    columns.put("floor_info", "VARCHAR(40) NOT NULL DEFAULT ''");
    columns.put("orientation", "VARCHAR(16) NOT NULL DEFAULT ''");
    columns.put("decoration", "VARCHAR(20) NOT NULL DEFAULT ''");
    columns.put("lease_term", "VARCHAR(40) NOT NULL DEFAULT ''");
    columns.put("payment_method", "VARCHAR(40) NOT NULL DEFAULT ''");
    columns.put("transport", "VARCHAR(160) NOT NULL DEFAULT ''");
    columns.put("available_from", "DATE");
    for (var entry : columns.entrySet())
      if (!existing.contains(entry.getKey()))
        db.update("ALTER TABLE t_house ADD COLUMN " + entry.getKey() + " " + entry.getValue());
  }
}
