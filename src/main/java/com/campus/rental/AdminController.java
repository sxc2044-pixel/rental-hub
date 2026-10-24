package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final Store db;
  private final ReservationService reservations;

  public AdminController(Store db, ReservationService reservations) {
    this.db = db;
    this.reservations = reservations;
  }

  @GetMapping("/houses")
  public List<Map<String, Object>> houses(@RequestParam(defaultValue = "") String status) {
    return status.isBlank()
        ? db.list("SELECT * FROM t_house ORDER BY id DESC LIMIT 1000")
        : db.list("SELECT * FROM t_house WHERE status=? ORDER BY id DESC LIMIT 1000", status);
  }

  private Object[] houseFields(Map<String, Object> d) {
    String region = text(d, "region", 1, 40), layout = text(d, "layout", 1, 40);
    require(
        db.count("SELECT COUNT(*) FROM t_dictionary WHERE category='REGION' AND label=?", region)
            > 0,
        "请选择有效区域");
    require(
        db.count("SELECT COUNT(*) FROM t_dictionary WHERE category='LAYOUT' AND label=?", layout)
            > 0,
        "请选择有效户型");
    String image = text(d, "image", 1, 100);
    require(
        image.matches("/images/room-[1-6]\\.svg")
            || image.matches("/api/media/[0-9a-f-]{36}\\.(png|jpg)"),
        "请选择房源插图或已上传的图片");
    return new Object[] {
      text(d, "title", 2, 100),
      region,
      text(d, "address", 2, 160),
      decimal(d, "rent", new BigDecimal("999999.99")),
      decimal(d, "area", new BigDecimal("9999.99")),
      layout,
      text(d, "facilities", 1, 255),
      text(d, "description", 1, 2000),
      image,
      text(d, "owner_name", 1, 40),
      phone(Map.of("phone", d.getOrDefault("owner_phone", "")))
    };
  }

  @PostMapping("/houses")
  @Transactional
  public Map<String, Object> createHouse(@RequestBody Map<String, Object> d, HttpSession s) {
    long id =
        db.insert(
            "INSERT INTO"
                + " t_house(title,region,address,rent,area,layout,facilities,description,image,owner_name,owner_phone)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            houseFields(d));
    db.audit(WebConfig.uid(s), "HOUSE_CREATE", "HOUSE", id, "代理录入，等待审核");
    return Map.of("id", id, "message", "房源已保存，等待审核");
  }

  @PutMapping("/houses/{id}")
  @Transactional
  public Map<String, Object> editHouse(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    db.one("SELECT id FROM t_house WHERE id=? AND status<>'DELETED' FOR UPDATE", id);
    var args = new ArrayList<>(Arrays.asList(houseFields(d)));
    args.add(id);
    db.update(
        "UPDATE t_house SET"
            + " title=?,region=?,address=?,rent=?,area=?,layout=?,facilities=?,description=?,image=?,owner_name=?,owner_phone=?,status='PENDING',reviewer_id=NULL,reviewed_at=NULL,review_note=NULL"
            + " WHERE id=?",
        args.toArray());
    db.audit(WebConfig.uid(s), "HOUSE_EDIT", "HOUSE", id, "房源变更后重新审核");
    return Map.of("message", "修改已保存，已重新进入审核");
  }

  @PutMapping("/houses/{id}/review")
  @Transactional
  public Map<String, Object> reviewHouse(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var h = db.one("SELECT status FROM t_house WHERE id=? FOR UPDATE", id);
    require("PENDING".equals(h.get("status")), "只能审核待审房源");
    String verdict = text(d, "status", 1, 16), note = text(d, "note", 1, 255);
    require(Set.of("PUBLISHED", "REJECTED").contains(verdict), "审核结论无效");
    db.update(
        "UPDATE t_house SET status=?,review_note=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        verdict,
        note,
        WebConfig.uid(s),
        id);
    db.audit(WebConfig.uid(s), "HOUSE_REVIEW_" + verdict, "HOUSE", id, note);
    return Map.of("message", "审核结果已保存");
  }

  @PutMapping("/houses/{id}/status")
  @Transactional
  public Map<String, Object> houseStatus(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var h = db.one("SELECT status,reviewer_id FROM t_house WHERE id=? FOR UPDATE", id);
    String target = text(d, "status", 1, 16), current = h.get("status").toString();
    require(
        (current.equals("PUBLISHED") && target.equals("OFFLINE"))
            || (current.equals("OFFLINE")
                && target.equals("PUBLISHED")
                && h.get("reviewer_id") != null),
        "房源状态不允许此操作");
    db.update("UPDATE t_house SET status=? WHERE id=?", target, id);
    db.audit(WebConfig.uid(s), "HOUSE_" + target, "HOUSE", id, "变更展示状态");
    return Map.of("message", "状态已更新");
  }

  @DeleteMapping("/houses/{id}")
  @Transactional
  public Map<String, Object> deleteHouse(@PathVariable long id, HttpSession s) {
    db.one("SELECT id FROM t_house WHERE id=? AND status<>'DELETED' FOR UPDATE", id);
    require(
        db.count(
                "SELECT COUNT(*) FROM t_reservation WHERE house_id=? AND status IN"
                    + " ('PENDING','CONFIRMED')",
                id)
            == 0,
        "仍有进行中的预约，请先处理");
    require(
        db.count(
                "SELECT COUNT(*) FROM t_contract WHERE house_id=? AND status IN"
                    + " ('INTENT','SIGNED')",
                id)
            == 0,
        "仍有有效合同记录，不能删除");
    db.update("UPDATE t_house SET status='DELETED' WHERE id=?", id);
    db.audit(WebConfig.uid(s), "HOUSE_DELETE", "HOUSE", id, "逻辑删除，保留业务关联");
    return Map.of("message", "已删除，历史记录保留");
  }

  @GetMapping("/users")
  public List<Map<String, Object>> users() {
    return db.list(
        "SELECT"
            + " id,account,name,role,school,phone,certification,cert_file,cert_note,enabled,created_at"
            + " FROM t_user ORDER BY id DESC LIMIT 1000");
  }

  @PutMapping("/users/{id}/enabled")
  @Transactional
  public Map<String, Object> enabled(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var u = db.one("SELECT role FROM t_user WHERE id=?", id);
    require(!"ADMIN".equals(u.get("role")), "不能禁用管理员");
    require(d.get("enabled") instanceof Boolean, "状态参数无效");
    boolean enabled = Boolean.TRUE.equals(d.get("enabled"));
    db.update(
        "UPDATE t_user SET enabled=?,session_version=session_version+1 WHERE id=?", enabled, id);
    db.audit(
        WebConfig.uid(s), enabled ? "USER_ENABLE" : "USER_DISABLE", "USER", id, "账号状态变更，旧会话失效");
    return Map.of("message", "账号状态已更新");
  }

  @PutMapping("/users/{id}/certification")
  @Transactional
  public Map<String, Object> certify(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var u = db.one("SELECT certification,cert_file FROM t_user WHERE id=? FOR UPDATE", id);
    require("PENDING".equals(u.get("certification")) && u.get("cert_file") != null, "用户尚未提交待审材料");
    String result = text(d, "status", 1, 16), note = text(d, "note", 1, 255);
    require(Set.of("APPROVED", "REJECTED").contains(result), "审核状态无效");
    db.update("UPDATE t_user SET certification=?,cert_note=? WHERE id=?", result, note, id);
    db.audit(WebConfig.uid(s), "CERT_" + result, "USER", id, note);
    return Map.of("message", "认证审核已完成");
  }

  @GetMapping("/reservations")
  public List<Map<String, Object>> reservations(@RequestParam(defaultValue = "") String status) {
    String sql =
        "SELECT r.*,h.title,u.name FROM t_reservation r JOIN t_house h ON r.house_id=h.id JOIN"
            + " t_user u ON r.user_id=u.id";
    return status.isBlank()
        ? db.list(sql + " ORDER BY r.id DESC LIMIT 1000")
        : db.list(sql + " WHERE r.status=? ORDER BY r.id DESC LIMIT 1000", status);
  }

  @PutMapping("/reservations/{id}/status")
  public Map<String, Object> reservationStatus(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    reservations.transition(WebConfig.uid(s), id, text(d, "status", 1, 16), true);
    return Map.of("message", "预约状态已更新");
  }

  @GetMapping("/messages")
  public List<Map<String, Object>> messages() {
    return db.list(
        "SELECT m.*,h.title,u.name FROM t_message m JOIN t_house h ON m.house_id=h.id JOIN t_user u"
            + " ON m.user_id=u.id ORDER BY m.id DESC LIMIT 1000");
  }

  @PutMapping("/messages/{id}/reply")
  @Transactional
  public Map<String, Object> reply(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    db.one("SELECT id FROM t_message WHERE id=?", id);
    db.update(
        "UPDATE t_message SET reply=?,replied_by=?,replied_at=CURRENT_TIMESTAMP WHERE id=?",
        text(d, "reply", 1, 1000),
        WebConfig.uid(s),
        id);
    db.audit(WebConfig.uid(s), "MESSAGE_REPLY", "MESSAGE", id, "回复咨询");
    return Map.of("message", "回复已保存");
  }

  @GetMapping("/comments")
  public List<Map<String, Object>> comments() {
    return db.list(
        "SELECT c.*,h.title,u.name FROM t_comment c JOIN t_house h ON c.house_id=h.id JOIN t_user u"
            + " ON c.user_id=u.id ORDER BY c.id DESC LIMIT 1000");
  }

  @PutMapping("/comments/{id}/review")
  @Transactional
  public Map<String, Object> commentReview(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var c = db.one("SELECT status FROM t_comment WHERE id=? FOR UPDATE", id);
    require("PENDING".equals(c.get("status")), "仅可审核待审评价");
    String target = text(d, "status", 1, 16), note = text(d, "note", 1, 255);
    require(Set.of("APPROVED", "REJECTED").contains(target), "审核结论无效");
    db.update("UPDATE t_comment SET status=?,review_note=? WHERE id=?", target, note, id);
    db.audit(WebConfig.uid(s), "COMMENT_" + target, "COMMENT", id, note);
    return Map.of("message", "评价审核已完成");
  }

  @GetMapping("/contracts")
  public List<Map<String, Object>> contracts() {
    return db.list(
        "SELECT c.*,h.title,u.name FROM t_contract c JOIN t_house h ON c.house_id=h.id JOIN t_user"
            + " u ON c.user_id=u.id ORDER BY c.id DESC LIMIT 1000");
  }

  @PutMapping("/contracts/{id}/status")
  @Transactional
  public Map<String, Object> contractStatus(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var c = db.one("SELECT * FROM t_contract WHERE id=? FOR UPDATE", id);
    String current = c.get("status").toString(), target = text(d, "status", 1, 16);
    require(
        (current.equals("INTENT") && Set.of("SIGNED", "CANCELLED").contains(target))
            || (current.equals("SIGNED") && target.equals("TERMINATED")),
        "合同状态转换不允许");
    if (target.equals("SIGNED")) {
      long house = ((Number) c.get("house_id")).longValue();
      db.one("SELECT id FROM t_house WHERE id=? FOR UPDATE", house);
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_contract WHERE house_id=? AND status='SIGNED' AND"
                      + " start_date<? AND end_date>?",
                  house,
                  c.get("end_date"),
                  c.get("start_date"))
              == 0,
          "该租期已有签约，不能重复出租");
    }
    db.update(
        "UPDATE t_contract SET status=?,signed_at="
            + (target.equals("SIGNED") ? "CURRENT_TIMESTAMP" : "signed_at")
            + " WHERE id=?",
        target,
        id);
    db.audit(WebConfig.uid(s), "CONTRACT_" + target, "CONTRACT", id, "线下核验后记录签约状态");
    return Map.of("message", "合同状态已更新");
  }

  @PostMapping("/notices")
  @Transactional
  public Map<String, Object> notice(@RequestBody Map<String, Object> d, HttpSession s) {
    long id =
        db.insert(
            "INSERT INTO t_notice(title,content,publisher_id) VALUES (?,?,?)",
            text(d, "title", 2, 100),
            text(d, "content", 1, 2000),
            WebConfig.uid(s));
    db.audit(WebConfig.uid(s), "NOTICE_CREATE", "NOTICE", id, "发布公告");
    return Map.of("message", "公告已发布", "id", id);
  }

  @PutMapping("/notices/{id}")
  @Transactional
  public Map<String, Object> editNotice(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    db.one("SELECT id FROM t_notice WHERE id=?", id);
    db.update(
        "UPDATE t_notice SET title=?,content=? WHERE id=?",
        text(d, "title", 2, 100),
        text(d, "content", 1, 2000),
        id);
    db.audit(WebConfig.uid(s), "NOTICE_EDIT", "NOTICE", id, "修改公告");
    return Map.of("message", "公告已修改");
  }

  @DeleteMapping("/notices/{id}")
  @Transactional
  public Map<String, Object> deleteNotice(@PathVariable long id, HttpSession s) {
    db.update("DELETE FROM t_notice WHERE id=?", id);
    db.audit(WebConfig.uid(s), "NOTICE_DELETE", "NOTICE", id, "删除公告");
    return Map.of("message", "公告已删除");
  }

  @PostMapping("/dictionaries")
  @Transactional
  public Map<String, Object> dictionary(@RequestBody Map<String, Object> d, HttpSession s) {
    String category = text(d, "category", 1, 16), label = text(d, "label", 1, 40);
    require(Set.of("REGION", "LAYOUT", "RENT").contains(category), "字典类别无效");
    BigDecimal min = null, max = null;
    if (category.equals("RENT")) {
      min = new BigDecimal(String.valueOf(d.get("min_value")));
      max = new BigDecimal(String.valueOf(d.get("max_value")));
      require(
          min.signum() >= 0
              && max.compareTo(min) > 0
              && max.compareTo(new BigDecimal("999999.99")) <= 0
              && min.scale() <= 2
              && max.scale() <= 2,
          "租金区间无效");
    }
    long id =
        db.insert(
            "INSERT INTO t_dictionary(category,label,min_value,max_value) VALUES (?,?,?,?)",
            category,
            label,
            min,
            max);
    db.audit(WebConfig.uid(s), "DICTIONARY_CREATE", "DICTIONARY", id, category + ":" + label);
    return Map.of("message", "字典已新增", "id", id);
  }

  @DeleteMapping("/dictionaries/{id}")
  @Transactional
  public Map<String, Object> deleteDictionary(@PathVariable long id, HttpSession s) {
    var d = db.one("SELECT * FROM t_dictionary WHERE id=? FOR UPDATE", id);
    String category = d.get("category").toString();
    if (!category.equals("RENT")) {
      String col = category.equals("REGION") ? "region" : "layout";
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_house WHERE " + col + "=? AND status<>'DELETED'",
                  d.get("label"))
              == 0,
          "字典已被房源引用，不能删除");
    }
    db.update("DELETE FROM t_dictionary WHERE id=?", id);
    db.audit(WebConfig.uid(s), "DICTIONARY_DELETE", "DICTIONARY", id, "删除未引用条目");
    return Map.of("message", "字典已删除");
  }

  @PutMapping("/dictionaries/{id}")
  @Transactional
  public Map<String, Object> editDictionary(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var old = db.one("SELECT * FROM t_dictionary WHERE id=? FOR UPDATE", id);
    String category = old.get("category").toString(), label = text(d, "label", 1, 40);
    BigDecimal min = null, max = null;
    if (category.equals("RENT")) {
      min = new BigDecimal(String.valueOf(d.get("min_value")));
      max = new BigDecimal(String.valueOf(d.get("max_value")));
      require(
          min.signum() >= 0
              && max.compareTo(min) > 0
              && max.compareTo(new BigDecimal("999999.99")) <= 0
              && min.scale() <= 2
              && max.scale() <= 2,
          "租金区间无效");
    }
    db.update(
        "UPDATE t_dictionary SET label=?,min_value=?,max_value=? WHERE id=?", label, min, max, id);
    if (!category.equals("RENT")) {
      String col = category.equals("REGION") ? "region" : "layout";
      db.update("UPDATE t_house SET " + col + "=? WHERE " + col + "=?", label, old.get("label"));
    }
    db.audit(WebConfig.uid(s), "DICTIONARY_EDIT", "DICTIONARY", id, "修改条目并同步房源引用");
    return Map.of("message", "字典与引用已同步更新");
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> audit() {
    return db.list(
        "SELECT a.*,u.name AS actor FROM t_audit a LEFT JOIN t_user u ON a.actor_id=u.id ORDER BY"
            + " a.id DESC LIMIT 200");
  }

  @GetMapping("/stats")
  public Map<String, Object> stats(
      @RequestParam(defaultValue = "") String region,
      @RequestParam(defaultValue = "") String layout,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to) {
    require(region.length() <= 40 && layout.length() <= 40, "统计条件过长");
    LocalDate start = from.isBlank() ? LocalDate.of(2000, 1, 1) : LocalDate.parse(from),
        end = to.isBlank() ? LocalDate.now() : LocalDate.parse(to);
    require(!start.isAfter(end), "开始日期不能晚于结束日期");
    String filter = " AND h.status<>'DELETED' AND (?='' OR h.region=?) AND (?='' OR h.layout=?)";
    Object[] a = {
      region, region, layout, layout, start.atStartOfDay(), end.plusDays(1).atStartOfDay()
    };
    long houses =
        db.count(
            "SELECT COUNT(*) FROM t_house h WHERE 1=1"
                + filter
                + " AND h.created_at>=? AND h.created_at<?",
            a);
    long users =
        db.count(
            "SELECT COUNT(*) FROM t_user WHERE role='STUDENT' AND created_at>=? AND created_at<?",
            a[4],
            a[5]);
    long reserves =
        db.count(
            "SELECT COUNT(*) FROM t_reservation r JOIN t_house h ON r.house_id=h.id WHERE 1=1"
                + filter
                + " AND r.created_at>=? AND r.created_at<?",
            a);
    long contracts =
        db.count(
            "SELECT COUNT(*) FROM t_contract c JOIN t_house h ON c.house_id=h.id WHERE"
                + " c.status='SIGNED'"
                + filter
                + " AND c.signed_at>=? AND c.signed_at<?",
            a);
    var distribution =
        db.list(
            "SELECT h.region,COUNT(*) AS total FROM t_house h WHERE 1=1"
                + filter
                + " AND h.created_at>=? AND h.created_at<? GROUP BY h.region ORDER BY total DESC",
            a);
    var trend =
        db.list(
            "SELECT CAST(r.created_at AS DATE) AS visit_day,COUNT(*) AS total FROM t_reservation r"
                + " JOIN t_house h ON r.house_id=h.id WHERE 1=1"
                + filter
                + " AND r.created_at>=? AND r.created_at<? GROUP BY CAST(r.created_at AS DATE)"
                + " ORDER BY visit_day",
            a);
    return Map.of(
        "houses",
        houses,
        "users",
        users,
        "reservations",
        reserves,
        "contracts",
        contracts,
        "distribution",
        distribution,
        "trend",
        trend,
        "scope",
        "房源按创建时间，预约包含全部状态，签约按签约时间；学生数仅按注册日期统计，不受区域或户型筛选影响。逻辑删除房源不计入关联统计。");
  }
}
