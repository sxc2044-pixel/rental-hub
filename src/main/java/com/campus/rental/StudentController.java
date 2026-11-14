package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StudentController {
  public static final String TERMS = ContractText.template();
  private final Store db;
  private final ReservationService service;

  public StudentController(Store db, ReservationService service) {
    this.db = db;
    this.service = service;
  }

  @GetMapping("/reservations")
  public List<Map<String, Object>> reservations(HttpSession s) {
    WebConfig.student(s);
    return db.list(
        "SELECT r.*,h.title FROM t_reservation r JOIN t_house h ON r.house_id=h.id WHERE"
            + " r.user_id=? ORDER BY r.id DESC",
        WebConfig.uid(s));
  }

  @PostMapping("/reservations")
  public Map<String, Object> reserve(@RequestBody Map<String, Object> data, HttpSession s) {
    WebConfig.student(s);
    long h = number(data, "houseId");
    var t = visit(text(data, "visitTime", 16, 19));
    return Map.of(
        "id", service.create(WebConfig.uid(s), h, t, phone(data)), "message", "预约已提交，等待确认");
  }

  @PutMapping("/reservations/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable long id, HttpSession s) {
    WebConfig.student(s);
    service.transition(WebConfig.uid(s), id, "CANCELLED", false);
    return Map.of("message", "预约已取消");
  }

  @GetMapping("/messages")
  public List<Map<String, Object>> messages(HttpSession s) {
    WebConfig.student(s);
    return db.list(
        "SELECT m.*,h.title FROM t_message m JOIN t_house h ON m.house_id=h.id WHERE m.user_id=?"
            + " ORDER BY m.id DESC",
        WebConfig.uid(s));
  }

  @PostMapping("/messages")
  public Map<String, Object> message(@RequestBody Map<String, Object> data, HttpSession s) {
    WebConfig.student(s);
    long h = number(data, "houseId");
    db.one("SELECT id FROM t_house WHERE id=? AND status='PUBLISHED'", h);
    long id =
        db.insert(
            "INSERT INTO t_message(user_id,house_id,content) VALUES (?,?,?)",
            WebConfig.uid(s),
            h,
            text(data, "content", 1, 1000));
    return Map.of("id", id, "message", "咨询已提交");
  }

  @GetMapping("/contract-template")
  public Map<String, String> template() {
    return Map.of("title", "城镇房屋租赁合同参考文本", "terms", TERMS);
  }

  @GetMapping("/contracts")
  public List<Map<String, Object>> contracts(HttpSession s) {
    WebConfig.student(s);
    return db.list(
        "SELECT c.*,h.title FROM t_contract c JOIN t_house h ON c.house_id=h.id WHERE c.user_id=?"
            + " ORDER BY c.id DESC",
        WebConfig.uid(s));
  }

  @PostMapping("/contracts")
  @Transactional
  public Map<String, Object> contract(@RequestBody Map<String, Object> data, HttpSession s) {
    WebConfig.student(s);
    long uid = WebConfig.uid(s), h = number(data, "houseId");
    var house = db.one("SELECT * FROM t_house WHERE id=? FOR UPDATE", h);
    require(house.get("owner_id") == null, "房东房源请通过租赁申请提交，由房东审批生成合同");
    require("PUBLISHED".equals(house.get("status")), "当前房源不可提交意向");
    require(
        db.count(
                "SELECT COUNT(*) FROM t_reservation WHERE user_id=? AND house_id=? AND"
                    + " status='COMPLETED'",
                uid,
                h)
            > 0,
        "请完成看房后再提交签约意向");
    require(
        db.count(
                "SELECT COUNT(*) FROM t_contract WHERE user_id=? AND house_id=? AND status IN"
                    + " ('INTENT','SIGNED')",
                uid,
                h)
            == 0,
        "已有有效签约记录");
    LocalDate start = LocalDate.parse(text(data, "startDate", 10, 10)),
        end = LocalDate.parse(text(data, "endDate", 10, 10));
    require(
        !start.isBefore(LocalDate.now()) && end.isAfter(start) && end.isBefore(start.plusYears(6)),
        "租期应从今日起，且不超过5年");
    require(Boolean.TRUE.equals(data.get("confirmed")), "请阅读条款并确认签约意向");
    long id =
        db.insert(
            "INSERT INTO t_contract(user_id,house_id,rent,start_date,end_date,terms) VALUES"
                + " (?,?,?,?,?,?)",
            uid,
            h,
            house.get("rent"),
            start,
            end,
            "托管房源意向草案：出租人尚未通过房东账号参与确认。本记录不代表双方已签署合同，押金及补充事项须另行协商。\n\n"
                + ContractText.create(house,
                    Map.of("start_date", start, "end_date", end),
                    Map.of("name", house.get("owner_name"), "id", "待核验",
                        "phone", house.get("owner_phone")),
                    db.one("SELECT id,name,phone FROM t_user WHERE id=?", uid),
                    "待双方约定", "待双方确认，平台不代替出租人签署。"));
    db.audit(uid, "CONTRACT_INTENT", "CONTRACT", id, "确认租赁意向");
    return Map.of("id", id, "message", "意向已提交，等待管理员处理");
  }

  @GetMapping("/comments")
  public List<Map<String, Object>> comments(HttpSession s) {
    WebConfig.student(s);
    return db.list(
        "SELECT c.*,h.title FROM t_comment c JOIN t_house h ON c.house_id=h.id WHERE c.user_id=?"
            + " ORDER BY c.id DESC",
        WebConfig.uid(s));
  }

  @PostMapping("/comments")
  @Transactional
  public Map<String, Object> comment(@RequestBody Map<String, Object> data, HttpSession s) {
    WebConfig.student(s);
    long uid = WebConfig.uid(s), h = number(data, "houseId"), rating = number(data, "rating");
    require(rating <= 5, "评分范围为1至5星");
    require(
        db.count(
                    "SELECT COUNT(*) FROM t_reservation WHERE user_id=? AND house_id=? AND"
                        + " status='COMPLETED'",
                    uid,
                    h)
                > 0
            || db.count(
                    "SELECT COUNT(*) FROM t_contract WHERE user_id=? AND house_id=? AND"
                        + " status='SIGNED'",
                    uid,
                    h)
                > 0,
        "完成看房或签约后才可评价");
    String content = text(data, "content", 1, 1000);
    var existing =
        db.list("SELECT id,status FROM t_comment WHERE user_id=? AND house_id=?", uid, h);
    long id;
    if (existing.isEmpty())
      id =
          db.insert(
              "INSERT INTO t_comment(user_id,house_id,rating,content) VALUES (?,?,?,?)",
              uid,
              h,
              rating,
              content);
    else {
      require("REJECTED".equals(existing.getFirst().get("status")), "每套房源仅能有一条评价，驳回后可修改重提");
      id = Store.id(existing.getFirst());
      db.update(
          "UPDATE t_comment SET rating=?,content=?,status='PENDING',review_note=NULL WHERE id=?",
          rating,
          content,
          id);
    }
    db.audit(uid, "COMMENT_SUBMIT", "COMMENT", id, "提交待审评价");
    return Map.of("id", id, "message", "评价已提交，审核通过后展示");
  }
}
