package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final Store db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

  private record Attempt(int count, Instant until) {}

  public AuthController(Store db) {
    this.db = db;
  }

  @GetMapping("/session")
  public Map<String, Object> session(HttpSession s) {
    var result = new HashMap<String, Object>();
    result.put("csrf", WebConfig.csrf(s));
    result.put("user", s.getAttribute("user"));
    return result;
  }

  @GetMapping("/captcha")
  public Map<String, Object> captcha(HttpSession s) {
    int a = random.nextInt(8) + 1, b = random.nextInt(8) + 1;
    s.setAttribute("captcha", Integer.toString(a + b));
    s.setAttribute("captchaTime", Instant.now());
    return Map.of("question", a + " + " + b + " = ?");
  }

  private void checkCaptcha(Map<String, Object> data, HttpSession s) {
    Object answer = s.getAttribute("captcha"), time = s.getAttribute("captchaTime");
    s.removeAttribute("captcha");
    require(
        answer != null
            && answer.equals(String.valueOf(data.get("captcha")))
            && time instanceof Instant t
            && t.plusSeconds(180).isAfter(Instant.now()),
        "验证码错误或已过期，请刷新验证码");
  }

  private void limit(String key) {
    if (attempts.size() > 10000)
      attempts.entrySet().removeIf(x -> x.getValue().until().isBefore(Instant.now()));
    var value = attempts.get(key);
    if (value != null && value.until().isAfter(Instant.now()) && value.count() >= 5)
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请15分钟后再试");
  }

  private void failed(String key) {
    attempts.compute(
        key,
        (k, v) ->
            v == null || v.until().isBefore(Instant.now())
                ? new Attempt(1, Instant.now().plusSeconds(900))
                : new Attempt(v.count() + 1, v.until()));
  }

  @PostMapping("/auth/register")
  @Transactional
  public Map<String, Object> register(
      @RequestBody Map<String, Object> data, HttpSession s, HttpServletRequest req) {
    String key = "register:" + req.getRemoteAddr();
    limit(key);
    failed(key);
    checkCaptcha(data, s);
    String account = text(data, "account", 6, 20);
    require(account.matches("[0-9]{6,20}"), "账号应为6至20位学号或手机号");
    String pass = password(data),
        name = text(data, "name", 1, 40),
        recovery = UUID.randomUUID().toString();
    long id =
        db.insert(
            "INSERT INTO t_user(account,password_hash,recovery_hash,name) VALUES (?,?,?,?)",
            account,
            encoder.encode(pass),
            encoder.encode(recovery),
            name);
    db.audit(id, "REGISTER", "USER", id, "学生账号注册");
    return Map.of("message", "注册成功，请妥善保管一次性展示的恢复密钥", "recoveryCode", recovery);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(
      @RequestBody Map<String, Object> data, HttpSession s, HttpServletRequest req) {
    String account = text(data, "account", 1, 32),
        key = "login:" + account + ":" + req.getRemoteAddr();
    limit(key);
    checkCaptcha(data, s);
    String pass = text(data, "password", 1, 72);
    var rows =
        db.list(
            "SELECT id,account,password_hash,name,role,enabled,session_version FROM t_user WHERE"
                + " account=?",
            account);
    if (rows.isEmpty() || !encoder.matches(pass, rows.getFirst().get("password_hash").toString())) {
      failed(key);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }
    var u = rows.getFirst();
    require(
        Boolean.TRUE.equals(u.get("enabled")) || Integer.valueOf(1).equals(u.get("enabled")),
        "账号已禁用");
    attempts.remove(key);
    u.remove("password_hash");
    req.changeSessionId();
    s.setAttribute("user", u);
    s.setAttribute("csrf", UUID.randomUUID().toString());
    db.audit(Store.id(u), "LOGIN", "USER", Store.id(u), "登录成功");
    return session(s);
  }

  @PostMapping("/auth/logout")
  public Map<String, Object> logout(HttpSession s) {
    s.invalidate();
    return Map.of("message", "已退出登录");
  }

  @PostMapping("/auth/reset")
  @Transactional
  public Map<String, Object> reset(
      @RequestBody Map<String, Object> data, HttpSession s, HttpServletRequest req) {
    String account = text(data, "account", 1, 32),
        key = "reset:" + account + ":" + req.getRemoteAddr();
    limit(key);
    checkCaptcha(data, s);
    var rows = db.list("SELECT id,recovery_hash FROM t_user WHERE account=?", account);
    String code = text(data, "recoveryCode", 1, 72), pass = password(data);
    if (rows.isEmpty() || !encoder.matches(code, rows.getFirst().get("recovery_hash").toString())) {
      failed(key);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号或恢复密钥错误");
    }
    long id = Store.id(rows.getFirst());
    String next = UUID.randomUUID().toString();
    db.update(
        "UPDATE t_user SET password_hash=?,recovery_hash=?,session_version=session_version+1 WHERE"
            + " id=?",
        encoder.encode(pass),
        encoder.encode(next),
        id);
    db.audit(id, "PASSWORD_RESET", "USER", id, "通过恢复密钥重置密码");
    attempts.remove(key);
    return Map.of("message", "密码已更新，原恢复密钥已作废", "recoveryCode", next);
  }

  @GetMapping("/me")
  public Map<String, Object> me(HttpSession s) {
    return db.one(
        "SELECT id,account,name,role,school,phone,certification,cert_file,cert_note,created_at FROM"
            + " t_user WHERE id=?",
        WebConfig.uid(s));
  }

  @PutMapping("/me")
  @Transactional
  public Map<String, Object> profile(@RequestBody Map<String, Object> data, HttpSession s) {
    long id = WebConfig.uid(s);
    String name = text(data, "name", 1, 40),
        school = text(data, "school", 1, 80),
        phone = phone(data);
    var previous = db.one("SELECT name,school FROM t_user WHERE id=?", id);
    boolean changed = !name.equals(previous.get("name")) || !school.equals(previous.get("school"));
    db.update(
        "UPDATE t_user SET name=?,school=?,phone=?"
            + (changed ? ",certification='NONE',cert_file=NULL,cert_note=NULL" : "")
            + " WHERE id=?",
        name,
        school,
        phone,
        id);
    db.audit(id, "PROFILE_UPDATE", "USER", id, changed ? "身份资料变化，认证已重置" : "更新联系方式");
    return me(s);
  }
}
