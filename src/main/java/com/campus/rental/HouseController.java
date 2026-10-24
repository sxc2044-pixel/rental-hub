package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HouseController {
  private final Store db;

  public HouseController(Store db) {
    this.db = db;
  }

  @GetMapping("/houses")
  public Map<String, Object> houses(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "") String region,
      @RequestParam(defaultValue = "") String layout,
      @RequestParam(required = false) BigDecimal minRent,
      @RequestParam(required = false) BigDecimal maxRent,
      @RequestParam(required = false) BigDecimal minArea,
      @RequestParam(required = false) BigDecimal maxArea,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "9") int size) {
    require(page >= 1 && page <= 100000 && size >= 1 && size <= 50, "分页参数超出范围");
    require(keyword.length() <= 100 && region.length() <= 40 && layout.length() <= 40, "筛选条件过长");
    for (BigDecimal n : Arrays.asList(minRent, maxRent, minArea, maxArea))
      if (n != null)
        require(n.signum() >= 0 && n.compareTo(new BigDecimal("99999999")) <= 0, "筛选值超出范围");
    require(minRent == null || maxRent == null || minRent.compareTo(maxRent) <= 0, "最低租金不能高于最高租金");
    require(minArea == null || maxArea == null || minArea.compareTo(maxArea) <= 0, "最小面积不能大于最大面积");
    StringBuilder where = new StringBuilder(" WHERE status='PUBLISHED'");
    var args = new ArrayList<Object>();
    if (!keyword.isBlank()) {
      where.append(" AND (title LIKE ? OR address LIKE ?)");
      args.add("%" + keyword + "%");
      args.add("%" + keyword + "%");
    }
    if (!region.isBlank()) {
      where.append(" AND region=?");
      args.add(region);
    }
    if (!layout.isBlank()) {
      where.append(" AND layout=?");
      args.add(layout);
    }
    String[] names = {"rent>=?", "rent<=?", "area>=?", "area<=?"};
    BigDecimal[] values = {minRent, maxRent, minArea, maxArea};
    for (int i = 0; i < values.length; i++)
      if (values[i] != null) {
        where.append(" AND ").append(names[i]);
        args.add(values[i]);
      }
    String order =
        switch (sort) {
          case "rentAsc" -> "rent ASC,id DESC";
          case "rentDesc" -> "rent DESC,id DESC";
          case "areaAsc" -> "area ASC,id DESC";
          case "areaDesc" -> "area DESC,id DESC";
          case "newest" -> "created_at DESC,id DESC";
          default -> throw new IllegalArgumentException("排序无效");
        };
    long count = db.count("SELECT COUNT(*) FROM t_house" + where, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        db.list(
            "SELECT id,title,region,address,rent,area,layout,facilities,image,created_at FROM"
                + " t_house"
                + where
                + " ORDER BY "
                + order
                + " LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of("items", rows, "total", count, "page", page, "size", size);
  }

  @GetMapping("/houses/{id}")
  public Map<String, Object> house(@PathVariable long id, HttpSession s) {
    var h =
        db.one(
            "SELECT"
                + " id,title,region,address,rent,area,layout,facilities,description,image,owner_name,owner_phone,status,created_at"
                + " FROM t_house WHERE id=? AND status='PUBLISHED'",
            id);
    String phone = h.get("owner_phone").toString();
    h.put(
        "owner_phone",
        phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : "联系管理员");
    h.put(
        "favorite",
        s.getAttribute("user") != null
            && db.count(
                    "SELECT COUNT(*) FROM t_favorite WHERE user_id=? AND house_id=?",
                    WebConfig.uid(s),
                    id)
                > 0);
    h.put(
        "comments",
        db.list(
            "SELECT c.id,c.rating,c.content,c.created_at,CONCAT(SUBSTRING(u.name,1,1),'同学') AS"
                + " author FROM t_comment c JOIN t_user u ON c.user_id=u.id WHERE c.house_id=? AND"
                + " c.status='APPROVED' ORDER BY c.id DESC",
            id));
    return h;
  }

  @GetMapping("/dictionaries")
  public List<Map<String, Object>> dictionaries() {
    return db.list("SELECT * FROM t_dictionary ORDER BY category,id");
  }

  @GetMapping("/houses/{id}/availability")
  public List<Map<String, Object>> availability(
      @PathVariable long id, @RequestParam java.time.LocalDate day) {
    db.one("SELECT id FROM t_house WHERE id=? AND status='PUBLISHED'", id);
    require(
        !day.isBefore(java.time.LocalDate.now())
            && !day.isAfter(java.time.LocalDate.now().plusDays(30)),
        "请选择未来30天内的日期");
    List<Map<String, Object>> slots = new ArrayList<>();
    for (int hour = 9; hour <= 18; hour++) {
      var time = day.atTime(hour, 0);
      boolean available =
          time.isAfter(java.time.LocalDateTime.now())
              && db.count(
                      "SELECT COUNT(*) FROM t_reservation WHERE house_id=? AND visit_time=? AND"
                          + " active_slot IS NOT NULL",
                      id,
                      time)
                  == 0;
      slots.add(Map.of("hour", String.format("%02d", hour), "available", available));
    }
    return slots;
  }

  @GetMapping("/notices")
  public List<Map<String, Object>> notices() {
    return db.list("SELECT id,title,content,created_at FROM t_notice ORDER BY id DESC LIMIT 20");
  }

  @GetMapping("/favorites")
  public List<Map<String, Object>> favorites(HttpSession s) {
    WebConfig.student(s);
    return db.list(
        "SELECT h.id,h.title,h.region,h.rent,h.area,h.layout,h.image,h.status FROM t_favorite f"
            + " JOIN t_house h ON f.house_id=h.id WHERE f.user_id=? ORDER BY f.created_at DESC",
        WebConfig.uid(s));
  }

  @PostMapping("/favorites/{id}")
  public Map<String, Object> favorite(@PathVariable long id, HttpSession s) {
    WebConfig.student(s);
    db.one("SELECT id FROM t_house WHERE id=? AND status='PUBLISHED'", id);
    if (db.count(
            "SELECT COUNT(*) FROM t_favorite WHERE user_id=? AND house_id=?", WebConfig.uid(s), id)
        == 0)
      db.update("INSERT INTO t_favorite(user_id,house_id) VALUES (?,?)", WebConfig.uid(s), id);
    return Map.of("message", "已收藏");
  }

  @DeleteMapping("/favorites/{id}")
  public Map<String, Object> unfavorite(@PathVariable long id, HttpSession s) {
    WebConfig.student(s);
    db.update("DELETE FROM t_favorite WHERE user_id=? AND house_id=?", WebConfig.uid(s), id);
    return Map.of("message", "已取消收藏");
  }
}
