package com.campus.rental;

import static com.campus.rental.Rules.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预约状态、并发占位和审计必须处于同一事务。 */
@Service
public class ReservationService {
  private final Store db;

  public ReservationService(Store db) {
    this.db = db;
  }

  @Transactional
  public long create(long user, long house, LocalDateTime time, String phone) {
    var h = db.one("SELECT id,status FROM t_house WHERE id=? FOR UPDATE", house);
    require("PUBLISHED".equals(h.get("status")), "房源当前不可预约");
    long id =
        db.insert(
            "INSERT INTO t_reservation(user_id,house_id,visit_time,phone,active_slot) VALUES"
                + " (?,?,?,?,?)",
            user,
            house,
            time,
            phone,
            house + ":" + time);
    db.audit(user, "RESERVE", "RESERVATION", id, "提交看房预约");
    return id;
  }

  @Transactional
  public void transition(long actor, long id, String target, boolean admin) {
    Map<String, Object> r = db.one("SELECT * FROM t_reservation WHERE id=? FOR UPDATE", id);
    require(admin || ((Number) r.get("user_id")).longValue() == actor, "只能操作本人预约");
    reservationTransition(r.get("status").toString(), target, admin);
    db.update(
        "UPDATE t_reservation SET status=?,active_slot=? WHERE id=?",
        target,
        "CANCELLED".equals(target) ? null : r.get("active_slot"),
        id);
    db.audit(actor, "RESERVATION_" + target, "RESERVATION", id, "更新预约状态");
  }
}
