package com.campus.rental;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Rules {
  private Rules() {}

  public static void require(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public static String text(Map<String, Object> data, String key, int min, int max) {
    Object raw = data.get(key);
    require(raw != null, "缺少字段 " + key);
    String source = String.valueOf(raw);
    require(!source.contains("\u0000"), "文本包含非法字符");
    String s = source.trim();
    require(s.length() >= min && s.length() <= max, key + " 长度应为 " + min + " 至 " + max + " 字符");
    require(!s.contains("\u0000"), "文本包含非法字符");
    return s;
  }

  public static String password(Map<String, Object> data) {
    String s = text(data, "password", 6, 72);
    require(s.matches(".*[A-Za-z].*") && s.matches(".*[0-9].*"), "密码须包含字母与数字");
    require(
        s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72, "密码 UTF-8 长度不能超过72字节");
    return s;
  }

  public static String phone(Map<String, Object> data) {
    String s = text(data, "phone", 11, 11);
    require(s.matches("1[3-9][0-9]{9}"), "请输入有效的11位手机号");
    return s;
  }

  public static long number(Map<String, Object> data, String key) {
    try {
      long n = Long.parseLong(String.valueOf(data.get(key)));
      require(n > 0, "编号无效");
      return n;
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 必须是正整数");
    }
  }

  public static BigDecimal decimal(Map<String, Object> data, String key, BigDecimal max) {
    try {
      BigDecimal n = new BigDecimal(String.valueOf(data.get(key)));
      require(n.signum() > 0 && n.compareTo(max) <= 0 && n.scale() <= 2, key + " 范围或精度不正确");
      return n;
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 必须是有效数值");
    }
  }

  public static LocalDateTime visit(String value) {
    try {
      var t = LocalDateTime.parse(value);
      require(
          t.isAfter(LocalDateTime.now()) && t.isBefore(LocalDateTime.now().plusDays(31)),
          "预约须在未来30天内");
      require(
          t.getMinute() == 0
              && t.getSecond() == 0
              && t.getNano() == 0
              && t.getHour() >= 9
              && t.getHour() <= 18,
          "预约时段为9至18点，每小时一档");
      return t;
    } catch (java.time.format.DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式错误");
    }
  }

  public static void reservationTransition(String current, String next, boolean admin) {
    boolean valid =
        admin
            ? (current.equals("PENDING") && (next.equals("CONFIRMED") || next.equals("CANCELLED")))
                || (current.equals("CONFIRMED")
                    && (next.equals("COMPLETED") || next.equals("CANCELLED")))
            : current.equals("PENDING") && next.equals("CANCELLED");
    require(valid, "当前预约状态不允许此操作");
  }
}
