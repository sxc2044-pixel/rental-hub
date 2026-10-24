package com.campus.rental;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("demo")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:catalogsamples;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "rental.catalog-samples=true",
      "logging.level.root=WARN"
    })
class SampleCatalogTest {
  @Autowired Store db;
  @Autowired SampleCatalog catalog;

  @Test
  void createsSequentialStudentsWithHashedPasswords() {
    assertEquals(26, db.count("SELECT COUNT(*) FROM t_user"));
    for (long n = 2023100514L; n <= 2023100537L; n++) {
      var user = db.one("SELECT password_hash,role FROM t_user WHERE account=?", Long.toString(n));
      assertEquals("STUDENT", user.get("role"));
      assertTrue(
          new BCryptPasswordEncoder().matches("Student123!", user.get("password_hash").toString()));
    }
  }

  @Test
  void enrichesCatalogWithPhotosAndStoredSpecifications() {
    assertEquals(34, db.count("SELECT COUNT(*) FROM t_house"));
    assertEquals(33, db.count("SELECT COUNT(*) FROM t_house WHERE status='PUBLISHED'"));
    assertEquals(
        34,
        db.count(
            "SELECT COUNT(*) FROM t_house WHERE image LIKE '/images/home-%.jpg' AND floor_info<>''"
                + " AND transport<>'' AND available_from IS NOT NULL"));
    assertEquals(
        3, db.count("SELECT COUNT(*) FROM t_house WHERE title LIKE '华师书院%' AND region='天河区'"));
    for (var row : db.list("SELECT floor_info FROM t_house")) {
      String[] floor = row.get("floor_info").toString().replace("层", "").split("/");
      assertTrue(Integer.parseInt(floor[0]) <= Integer.parseInt(floor[1]));
    }
  }

  @Test
  void repeatedStartupDoesNotDuplicateOrOverwriteEditedRecords() throws Exception {
    db.update("UPDATE t_user SET name='保留已编辑姓名',enabled=false WHERE account='2023100514'");
    long id =
        Store.id(db.one("SELECT id FROM t_house WHERE title LIKE '暨南雅苑%' ORDER BY id LIMIT 1"));
    db.update("UPDATE t_house SET description='保留用户编辑的介绍',region='海珠区' WHERE id=?", id);
    catalog.run();
    catalog.run();
    assertEquals("海珠区", db.one("SELECT region FROM t_house WHERE id=?", id).get("region"));
    assertEquals(26, db.count("SELECT COUNT(*) FROM t_user"));
    assertEquals(34, db.count("SELECT COUNT(*) FROM t_house"));
    assertEquals(
        "保留已编辑姓名", db.one("SELECT name FROM t_user WHERE account='2023100514'").get("name"));
    assertEquals(
        "保留用户编辑的介绍", db.one("SELECT description FROM t_house WHERE id=?", id).get("description"));
  }
}
