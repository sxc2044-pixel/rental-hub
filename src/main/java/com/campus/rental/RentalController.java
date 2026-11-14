package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 房东与租客共享业务；每次操作都从数据库验证对象归属及状态。 */
@RestController
@RequestMapping("/api/rental")
public class RentalController {
  private final Store db;
  private final AdminController houses;
  private final AuthController auth;

  public RentalController(Store db, AdminController houses, AuthController auth) {
    this.db = db;
    this.houses = houses;
    this.auth = auth;
  }

  private long owner(HttpSession s) {
    require("LANDLORD".equals(WebConfig.user(s).get("role")), "需要房东账号");
    return WebConfig.uid(s);
  }

  private long tenant(HttpSession s) {
    WebConfig.student(s);
    return WebConfig.uid(s);
  }

  private Map<String, Object> ownHouse(long id, HttpSession s) {
    return db.one(
        "SELECT * FROM t_house WHERE id=? AND owner_id=? AND status<>'DELETED' FOR UPDATE",
        id,
        owner(s));
  }

  private Map<String, Object> contract(long id, HttpSession s) {
    return db.one(
        "SELECT * FROM t_contract WHERE id=? AND (landlord_id=? OR user_id=?) FOR UPDATE",
        id,
        WebConfig.uid(s),
        WebConfig.uid(s));
  }

  private Map<String, Object> ok() {
    return Map.of("message", "操作已保存");
  }

  private List<Long> ids(Map<String, Object> d) {
    require(d.get("ids") instanceof List<?>, "请选择记录");
    var list = (List<?>) d.get("ids");
    require(!list.isEmpty() && list.size() <= 100, "每次选择1至100条");
    return list.stream().map(v -> number(Map.of("id", v), "id")).distinct().sorted().toList();
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(HttpSession s) {
    long u = owner(s);
    var start = LocalDate.now().withDayOfMonth(1);
    return Map.of(
        "houses",
        db.count("SELECT COUNT(*) FROM t_house WHERE owner_id=? AND status<>'DELETED'", u),
        "pending",
        db.count("SELECT COUNT(*) FROM t_house WHERE owner_id=? AND status='PENDING'", u),
        "contracts",
        db.count(
            "SELECT COUNT(*) FROM t_contract WHERE landlord_id=? AND status='SIGNED' AND"
                + " end_date>=?",
            u,
            LocalDate.now()),
        "income",
        db.one(
                "SELECT COALESCE(SUM(amount),0) AS total FROM t_bill WHERE landlord_id=? AND"
                    + " status='PAID' AND paid_at>=? AND paid_at<?",
                u,
                start.atStartOfDay(),
                start.plusMonths(1).atStartOfDay())
            .get("total"),
        "repairs",
        db.count("SELECT COUNT(*) FROM t_repair WHERE landlord_id=? AND status<>'COMPLETED'", u));
  }

  @GetMapping("/houses")
  public Object houses(HttpSession s) {
    return db.list(
        "SELECT * FROM t_house WHERE owner_id=? AND status<>'DELETED' ORDER BY id DESC", owner(s));
  }

  @PostMapping("/houses")
  @Transactional
  public Object createHouse(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = owner(s);
    var result = houses.createHouse(d, s);
    long id = ((Number) result.get("id")).longValue();
    db.update("UPDATE t_house SET owner_id=? WHERE id=?", u, id);
    return result;
  }

  @PutMapping("/houses/{id}")
  @Transactional
  public Object editHouse(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    ownHouse(id, s);
    return houses.editHouse(id, d, s);
  }

  @PutMapping("/houses/{id}/status")
  @Transactional
  public Object statusHouse(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    ownHouse(id, s);
    return houses.houseStatus(id, d, s);
  }

  @DeleteMapping("/houses/{id}")
  @Transactional
  public Object deleteHouse(@PathVariable long id, HttpSession s) {
    ownHouse(id, s);
    return houses.deleteHouse(id, s);
  }

  @PostMapping("/houses/batch-delete")
  @Transactional
  public Object deleteHouses(@RequestBody Map<String, Object> d, HttpSession s) {
    owner(s);
    for (long id : ids(d)) deleteHouse(id, s);
    return ok();
  }

  @GetMapping("/applications")
  public Object applications(HttpSession s) {
    long u = WebConfig.uid(s);
    return db.list(
        "SELECT a.*,h.title,u.name AS tenant FROM t_application a JOIN t_house h ON a.house_id=h.id"
            + " JOIN t_user u ON a.user_id=u.id WHERE a.landlord_id=? OR a.user_id=? ORDER BY a.id"
            + " DESC",
        u,
        u);
  }

  @PostMapping("/applications")
  @Transactional
  public Object apply(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = tenant(s), h = number(d, "houseId");
    var house =
        db.one(
            "SELECT * FROM t_house WHERE id=? AND status='PUBLISHED' AND owner_id IS NOT NULL FOR"
                + " UPDATE",
            h);
    LocalDate start = LocalDate.parse(text(d, "startDate", 10, 10)),
        end = LocalDate.parse(text(d, "endDate", 10, 10));
    require(
        !start.isBefore(LocalDate.now()) && end.isAfter(start) && !end.isAfter(start.plusYears(5)),
        "租期应从今日起，且不超过5年");
    require(
        db.count(
                "SELECT COUNT(*) FROM t_application WHERE house_id=? AND user_id=? AND"
                    + " status='PENDING'",
                h,
                u)
            == 0,
        "已有待处理申请");
    long id =
        db.insert(
            "INSERT INTO t_application(house_id,user_id,landlord_id,start_date,end_date,note)"
                + " VALUES(?,?,?,?,?,?)",
            h,
            u,
            house.get("owner_id"),
            start,
            end,
            text(d, "note", 1, 1000));
    return Map.of("id", id, "message", "租赁申请已提交");
  }

  @PutMapping("/applications/{id}/review")
  @Transactional
  public Object review(@PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    long u = owner(s); // 同一房源串行批准，合同租期占位包含未签署草稿。
    var lookup = db.one("SELECT house_id FROM t_application WHERE id=? AND landlord_id=?", id, u);
    var h = ownHouse(((Number) lookup.get("house_id")).longValue(), s);
    var a = db.one("SELECT * FROM t_application WHERE id=? AND landlord_id=? FOR UPDATE", id, u);
    require("PENDING".equals(a.get("status")), "申请已处理");
    String verdict = text(d, "status", 1, 16), note = text(d, "note", 1, 255);
    require(Set.of("APPROVED", "REJECTED").contains(verdict), "审批结论无效");
    Long cid = null;
    if (verdict.equals("APPROVED")) {
      require("PUBLISHED".equals(h.get("status")), "房源当前未上架");
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_contract WHERE house_id=? AND status IN"
                      + " ('DRAFT','SIGNED') AND start_date<? AND end_date>?",
                  h.get("id"),
                  a.get("end_date"),
                  a.get("start_date"))
              == 0,
          "该租期已有合同占用");
      String deposit = String.valueOf(d.getOrDefault("deposit", "0"));
      try {
        var amount = new BigDecimal(deposit);
        require(amount.signum() >= 0 && amount.scale() <= 2
            && amount.compareTo(new BigDecimal("999999.99")) <= 0, "押金金额无效");
        deposit = amount.toPlainString();
      } catch (NumberFormatException ex) { throw new IllegalArgumentException("押金须为数字"); }
      String terms = ContractText.create(h, a,
          db.one("SELECT id,name,phone FROM t_user WHERE id=?", u),
          db.one("SELECT id,name,phone FROM t_user WHERE id=?", a.get("user_id")),
          deposit, text(Map.of("extra", d.getOrDefault("extra", "无。")), "extra", 1, 400));
      require(terms.length() <= 3000, "合同正文过长，请缩短补充约定");
      cid =
          db.insert(
              "INSERT INTO"
                  + " t_contract(house_id,user_id,landlord_id,rent,start_date,end_date,status,terms)"
                  + " VALUES(?,?,?,?,?,?,'DRAFT',?)",
              h.get("id"),
              a.get("user_id"),
              u,
              h.get("rent"),
              a.get("start_date"),
              a.get("end_date"),
              terms);
    }
    db.update(
        "UPDATE t_application SET status=?,review_note=?,contract_id=? WHERE id=?",
        verdict,
        note,
        cid,
        id);
    db.audit(u, "APPLICATION_" + verdict, "APPLICATION", id, note);
    return ok();
  }

  @GetMapping("/contracts")
  public Object contracts(HttpSession s) {
    long u = WebConfig.uid(s);
    var rows = db.list(
        "SELECT c.*,h.title FROM t_contract c JOIN t_house h ON c.house_id=h.id WHERE"
            + " c.landlord_id=? OR (c.user_id=? AND c.landlord_id IS NOT NULL) ORDER BY c.id DESC", u, u);
    for (var c : rows) c.put("terms_hash", ContractText.hash(c.get("terms")));
    return rows;
  }

  @PutMapping("/contracts/{id}/sign")
  @Transactional
  public Object sign(@PathVariable long id, @RequestBody Map<String,Object> d, HttpSession s) {
    var c = contract(id, s);
    require("DRAFT".equals(c.get("status")), "仅待签署合同可签署");
    boolean landlord =
        Objects.equals(((Number) c.get("landlord_id")).longValue(), WebConfig.uid(s));
    String column = landlord ? "landlord_signed" : "tenant_signed";
    require(
        !Boolean.TRUE.equals(c.get(column)) && !Integer.valueOf(1).equals(c.get(column)), "已经签署");
    require(Boolean.TRUE.equals(d.get("consent")) || "true".equals(d.get("consent")), "请阅读并同意电子确认条款");
    String hash = ContractText.hash(c.get("terms"));
    require(hash.equals(text(d, "termsHash", 64, 64)), "合同内容已变化，请重新阅读");
    for (String party : List.of("landlord", "tenant")) {
      Object signedHash = c.get(party + "_signed_hash");
      require(signedHash == null || hash.equals(signedHash), "合同与已签署正文不一致，请联系管理员核查");
    }
    var user = db.one("SELECT name,password_hash FROM t_user WHERE id=?", WebConfig.uid(s));
    String name = text(d, "signerName", 1, 40);
    require(name.equals(user.get("name")), "签署姓名须与个人资料一致");
    String key = "contract-sign:" + WebConfig.uid(s);
    auth.limit(key);
    if (!new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        .matches(text(d, "password", 1, 72), user.get("password_hash").toString())) {
      auth.failed(key);
      throw new IllegalArgumentException("密码复核失败");
    }
    String party = landlord ? "landlord" : "tenant";
    db.update("UPDATE t_contract SET " + column + "=TRUE," + party + "_signed_name=?,"
        + party + "_signed_time=CURRENT_TIMESTAMP," + party + "_signed_hash=? WHERE id=?", name, hash, id);
    db.update(
        "UPDATE t_contract SET status='SIGNED',signed_at=CURRENT_TIMESTAMP WHERE id=? AND"
            + " tenant_signed=TRUE AND landlord_signed=TRUE",
        id);
    db.audit(WebConfig.uid(s), "CONTRACT_SIGN", "CONTRACT", id, (landlord ? "房东" : "租客") + "确认签署；SHA-256=" + hash);
    return ok();
  }

  @GetMapping("/moveouts")
  public Object moveouts(HttpSession s) {
    long u = WebConfig.uid(s);
    return db.list(
        "SELECT m.*,h.title FROM t_moveout m JOIN t_contract c ON c.id=m.contract_id JOIN t_house h"
            + " ON h.id=c.house_id WHERE m.user_id=? OR m.landlord_id=? ORDER BY m.id DESC",
        u,
        u);
  }

  @PostMapping("/moveouts")
  @Transactional
  public Object moveout(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = tenant(s), id = number(d, "contractId");
    var c = contract(id, s);
    require("SIGNED".equals(c.get("status")) && c.get("landlord_id") != null, "仅有效房东合同可以退租");
    require(
        db.count("SELECT COUNT(*) FROM t_moveout WHERE contract_id=? AND status='PENDING'", id)
            == 0,
        "已有退租申请");
    db.insert(
        "INSERT INTO t_moveout(contract_id,user_id,landlord_id,reason) VALUES(?,?,?,?)",
        id,
        u,
        c.get("landlord_id"),
        text(d, "reason", 1, 1000));
    return ok();
  }

  @PutMapping("/moveouts/{id}/review")
  @Transactional
  public Object reviewMoveout(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var m = db.one("SELECT * FROM t_moveout WHERE id=? AND landlord_id=? FOR UPDATE", id, owner(s));
    var c = contract(((Number) m.get("contract_id")).longValue(), s);
    require("PENDING".equals(m.get("status")) && "SIGNED".equals(c.get("status")), "退租状态已变化");
    String verdict = text(d, "status", 1, 16);
    require(Set.of("APPROVED", "REJECTED").contains(verdict), "审批结论无效");
    String note = text(d, "note", 1, 255);
    if (verdict.equals("APPROVED")) {
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_bill WHERE contract_id=? AND status='UNPAID'",
                  c.get("id"))
              == 0,
          "请先结清账单");
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_repair WHERE contract_id=? AND status<>'COMPLETED'",
                  c.get("id"))
              == 0,
          "请先完成报修");
      db.update("UPDATE t_contract SET status='TERMINATED' WHERE id=?", c.get("id"));
    }
    db.update("UPDATE t_moveout SET status=?,review_note=? WHERE id=?", verdict, note, id);
    db.audit(WebConfig.uid(s), "MOVEOUT_" + verdict, "MOVEOUT", id, note);
    return ok();
  }

  @GetMapping("/bills")
  public Object bills(HttpSession s) {
    long u = WebConfig.uid(s);
    return db.list(
        "SELECT b.*,h.title FROM t_bill b JOIN t_contract c ON c.id=b.contract_id JOIN t_house h ON"
            + " h.id=c.house_id WHERE b.deleted=FALSE AND (b.landlord_id=? OR b.user_id=?) ORDER BY"
            + " b.id DESC",
        u,
        u);
  }

  @PostMapping("/bills")
  @Transactional
  public Object bill(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = owner(s), id = number(d, "contractId");
    var c = contract(id, s);
    require(
        Objects.equals(((Number) c.get("landlord_id")).longValue(), u)
            && "SIGNED".equals(c.get("status")),
        "仅本人有效合同可开单");
    LocalDate month = YearMonth.parse(text(d, "month", 7, 7)).atDay(1),
        due = LocalDate.parse(text(d, "dueDate", 10, 10));
    require(
        !month.isBefore(((java.sql.Date) c.get("start_date")).toLocalDate().withDayOfMonth(1))
            && !month.isAfter(((java.sql.Date) c.get("end_date")).toLocalDate()),
        "账期须位于租期内");
    require(
        db.count("SELECT COUNT(*) FROM t_bill WHERE contract_id=? AND bill_month=?", id, month)
            == 0,
        "该合同本账期已开单");
    db.insert(
        "INSERT INTO t_bill(contract_id,user_id,landlord_id,bill_month,due_date,amount,note)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        c.get("user_id"),
        u,
        month,
        due,
        decimal(d, "amount", new BigDecimal("9999999.99")),
        text(d, "note", 1, 255));
    return ok();
  }

  @PutMapping("/bills/{id}/settle")
  @Transactional
  public Object settle(@PathVariable long id, HttpSession s) {
    var b =
        db.one(
            "SELECT * FROM t_bill WHERE id=? AND landlord_id=? AND deleted=FALSE FOR UPDATE",
            id,
            owner(s));
    require("UNPAID".equals(b.get("status")), "账单已结清");
    db.update("UPDATE t_bill SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE id=?", id);
    db.audit(WebConfig.uid(s), "BILL_SETTLE", "BILL", id, "登记线下收款");
    return ok();
  }

  @PostMapping("/bills/batch-delete")
  @Transactional
  public Object deleteBills(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = owner(s);
    for (long id : ids(d)) {
      var b =
          db.one(
              "SELECT status FROM t_bill WHERE id=? AND landlord_id=? AND deleted=FALSE FOR UPDATE",
              id,
              u);
      require("PAID".equals(b.get("status")), "只能删除已结清账单");
      db.update("UPDATE t_bill SET deleted=TRUE WHERE id=?", id);
      db.audit(u, "BILL_ARCHIVE", "BILL", id, "已结清账单逻辑删除，保留收入统计");
    }
    return ok();
  }

  @GetMapping("/repairs")
  public Object repairs(HttpSession s) {
    long u = WebConfig.uid(s);
    return db.list(
        "SELECT r.*,h.title FROM t_repair r JOIN t_contract c ON c.id=r.contract_id JOIN t_house h"
            + " ON h.id=c.house_id WHERE r.user_id=? OR r.landlord_id=? ORDER BY r.id DESC",
        u,
        u);
  }

  @PostMapping("/repairs")
  @Transactional
  public Object repair(@RequestBody Map<String, Object> d, HttpSession s) {
    long u = tenant(s);
    var c = contract(number(d, "contractId"), s);
    require("SIGNED".equals(c.get("status")) && c.get("landlord_id") != null, "仅有效房东合同可以报修");
    db.insert(
        "INSERT INTO t_repair(contract_id,user_id,landlord_id,description) VALUES(?,?,?,?)",
        c.get("id"),
        u,
        c.get("landlord_id"),
        text(d, "description", 1, 2000));
    return ok();
  }

  @PutMapping("/repairs/{id}")
  @Transactional
  public Object updateRepair(
      @PathVariable long id, @RequestBody Map<String, Object> d, HttpSession s) {
    var r = db.one("SELECT * FROM t_repair WHERE id=? AND landlord_id=? FOR UPDATE", id, owner(s));
    String next = text(d, "status", 1, 16);
    require(
        ("PENDING".equals(r.get("status")) && next.equals("ASSIGNED"))
            || ("ASSIGNED".equals(r.get("status")) && next.equals("COMPLETED")),
        "请按接单派工、确认完成顺序处理");
    String worker = text(d, "worker", 1, 80), responsibility = text(d, "responsibility", 1, 16);
    require(Set.of("LANDLORD", "TENANT", "SHARED").contains(responsibility), "责任方无效");
    BigDecimal cost;
    try {
      cost = new BigDecimal(String.valueOf(d.get("cost")));
    } catch (Exception ex) {
      throw new IllegalArgumentException("维修费用须为数字");
    }
    require(
        cost.signum() >= 0 && cost.scale() <= 2 && cost.compareTo(new BigDecimal("999999.99")) <= 0,
        "费用应为0至999999.99");
    db.update(
        "UPDATE t_repair SET status=?,worker=?,cost=?,responsibility=?,result_note=? WHERE id=?",
        next,
        worker,
        cost,
        responsibility,
        text(d, "note", 1, 255),
        id);
    db.audit(WebConfig.uid(s), "REPAIR_" + next, "REPAIR", id, "维修状态与费用登记");
    return ok();
  }

  @GetMapping("/contacts")
  public Object contacts(HttpSession s) {
    long u = WebConfig.uid(s);
    return db.list(
        "SELECT DISTINCT u.id,u.name,u.role,u.avatar FROM t_user u WHERE u.enabled=TRUE AND u.id<>?"
            + " AND (u.id IN (SELECT sender_id FROM t_chat WHERE receiver_id=?) OR u.id IN (SELECT"
            + " receiver_id FROM t_chat WHERE sender_id=?) OR u.id IN (SELECT user_id FROM"
            + " t_application WHERE landlord_id=?) OR u.id IN (SELECT landlord_id FROM"
            + " t_application WHERE user_id=?))",
        u,
        u,
        u,
        u,
        u);
  }

  @GetMapping("/unread")
  public Object unread(HttpSession s) {
    return Map.of(
        "count",
        db.count(
            "SELECT COUNT(*) FROM t_chat WHERE receiver_id=? AND read_at IS NULL",
            WebConfig.uid(s)));
  }

  private void chatPeer(long peer, HttpSession s) {
    var p = db.one("SELECT id,role FROM t_user WHERE id=? AND enabled=TRUE", peer);
    String mine = WebConfig.user(s).get("role").toString();
    require(
        (mine.equals("LANDLORD") && p.get("role").equals("STUDENT"))
            || (mine.equals("STUDENT") && p.get("role").equals("LANDLORD")),
        "仅支持房东与租客沟通");
  }

  @GetMapping("/chat/{peer}")
  public Object chat(
      @PathVariable long peer, @RequestParam(defaultValue = "0") long after, HttpSession s) {
    chatPeer(peer, s);
    return db.list(
        "SELECT m.*,h.title,h.image,h.rent,h.status AS house_status FROM t_chat m LEFT JOIN t_house"
            + " h ON m.house_id=h.id WHERE m.id>? AND ((m.sender_id=? AND m.receiver_id=?) OR"
            + " (m.sender_id=? AND m.receiver_id=?)) ORDER BY m.id LIMIT 200",
        after,
        WebConfig.uid(s),
        peer,
        peer,
        WebConfig.uid(s));
  }

  @PutMapping("/chat/{peer}/read")
  public Object read(@PathVariable long peer, @RequestBody Map<String, Object> d, HttpSession s) {
    chatPeer(peer, s);
    db.update(
        "UPDATE t_chat SET read_at=CURRENT_TIMESTAMP WHERE receiver_id=? AND sender_id=? AND id<=?"
            + " AND read_at IS NULL",
        WebConfig.uid(s),
        peer,
        number(d, "through"));
    return ok();
  }

  @PostMapping("/chat/{peer}")
  public Object send(@PathVariable long peer, @RequestBody Map<String, Object> d, HttpSession s) {
    chatPeer(peer, s);
    String type = text(d, "type", 1, 16), content = text(d, "content", 0, 1000);
    Long hid = null;
    require(Set.of("TEXT", "IMAGE", "HOUSE").contains(type), "消息类型无效");
    if (type.equals("TEXT")) require(!content.isBlank(), "请输入消息");
    if (type.equals("IMAGE"))
      require(
          db.count(
                  "SELECT COUNT(*) FROM t_media WHERE owner_id=? AND url=?",
                  WebConfig.uid(s),
                  content)
              > 0,
          "请选择本人上传的图片");
    if (type.equals("HOUSE")) {
      hid = number(d, "houseId");
      db.one(
          "SELECT id FROM t_house WHERE id=? AND status='PUBLISHED' AND owner_id=?",
          hid,
          "LANDLORD".equals(WebConfig.user(s).get("role")) ? WebConfig.uid(s) : peer);
      content = "房源卡片";
    }
    long id =
        db.insert(
            "INSERT INTO t_chat(sender_id,receiver_id,type,content,house_id) VALUES(?,?,?,?,?)",
            WebConfig.uid(s),
            peer,
            type,
            content,
            hid);
    return Map.of("id", id, "message", "已发送");
  }
}
