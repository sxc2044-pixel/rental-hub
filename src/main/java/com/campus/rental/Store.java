package com.campus.rental;

import java.sql.PreparedStatement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** 所有用户输入以占位参数绑定，动态列名仅来自代码中的白名单。 */
@Repository
public class Store {
  final JdbcTemplate jdbc;

  public Store(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public javax.sql.DataSource dataSource() {
    return Objects.requireNonNull(jdbc.getDataSource());
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或不可访问");
    return rows.getFirst();
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          PreparedStatement ps = c.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public void audit(long actor, String action, String entity, long id, String detail) {
    update(
        "INSERT INTO t_audit(actor_id,action,entity_type,entity_id,detail) VALUES (?,?,?,?,?)",
        actor,
        action,
        entity,
        id,
        detail);
  }

  public static long id(Map<String, Object> row) {
    return ((Number) row.get("id")).longValue();
  }
}
