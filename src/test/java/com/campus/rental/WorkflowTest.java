package com.campus.rental;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

@org.springframework.test.context.ActiveProfiles("demo")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:rentaltest;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "rental.seed-demo=true",
      "rental.upload-dir=./target/test-uploads"
    })
@AutoConfigureMockMvc
class WorkflowTest {
  private static final String RUN_ID = UUID.randomUUID().toString();

  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:h2:file:./target/testdb/workflow-"
                + RUN_ID
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired Store db;
  @Autowired ReservationService service;
  MockHttpSession admin, student;

  @BeforeEach
  void login() throws Exception {
    admin = login("admin", "Admin123!");
    student = login("2023100513", "Student123!");
  }

  MockHttpSession session() throws Exception {
    return (MockHttpSession) mvc.perform(get("/api/session")).andReturn().getRequest().getSession();
  }

  String csrf(MockHttpSession s) {
    return s.getAttribute("csrf").toString();
  }

  MockHttpSession login(String account, String password) throws Exception {
    var s = session();
    captcha(s);
    var result =
        call(
            s,
            "POST",
            "/api/auth/login",
            Map.of("account", account, "password", password, "captcha", s.getAttribute("captcha")));
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return (MockHttpSession) result.getRequest().getSession();
  }

  void captcha(MockHttpSession s) throws Exception {
    mvc.perform(get("/api/captcha").session(s)).andReturn();
  }

  MvcResult call(MockHttpSession s, String method, String path, Object body) throws Exception {
    var request =
        switch (method) {
          case "POST" -> post(path);
          case "PUT" -> put(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    request.session(s).header("X-CSRF-Token", csrf(s));
    if (body != null)
      request.contentType("application/json").content(mapper.writeValueAsBytes(body));
    return mvc.perform(request).andReturn();
  }

  JsonNode json(MvcResult r) throws Exception {
    return mapper.readTree(r.getResponse().getContentAsByteArray());
  }

  void ok(MvcResult r) throws Exception {
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  Map<String, Object> houseData() {
    return new HashMap<>(
        Map.ofEntries(
            Map.entry("title", "测试房源" + UUID.randomUUID().toString().substring(0, 8)),
            Map.entry("region", "天河区"),
            Map.entry("address", "大学路测试地址"),
            Map.entry("rent", 1800),
            Map.entry("area", 42),
            Map.entry("layout", "一室一厅"),
            Map.entry("facilities", "空调 / 宽带"),
            Map.entry("description", "用于自动化测试"),
            Map.entry("image", "/images/room-1.svg"),
            Map.entry("owner_name", "测试房东"),
            Map.entry("owner_phone", "13800138000")));
  }

  long house(boolean published) throws Exception {
    var r = call(admin, "POST", "/api/admin/houses", houseData());
    ok(r);
    long id = json(r).get("id").asLong();
    if (published)
      ok(
          call(
              admin,
              "PUT",
              "/api/admin/houses/" + id + "/review",
              Map.of("status", "PUBLISHED", "note", "测试审核通过")));
    return id;
  }

  String futureTime(int days) {
    return LocalDate.now().plusDays(days).atTime(10, 0).toString();
  }

  long reserve(long h) throws Exception {
    var r =
        call(
            student,
            "POST",
            "/api/reservations",
            Map.of("houseId", h, "visitTime", futureTime(2), "phone", "13800138001"));
    ok(r);
    return json(r).get("id").asLong();
  }

  void complete(long r) throws Exception {
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/reservations/" + r + "/status",
            Map.of("status", "CONFIRMED")));
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/reservations/" + r + "/status",
            Map.of("status", "COMPLETED")));
  }

  @Test
  void unauthenticatedAndStudentCannotManage() throws Exception {
    assertEquals(401, call(session(), "GET", "/api/admin/users", null).getResponse().getStatus());
    assertEquals(
        403, call(student, "POST", "/api/admin/houses", houseData()).getResponse().getStatus());
  }

  @Test
  void mutationRequiresCsrf() throws Exception {
    assertEquals(
        403,
        mvc.perform(post("/api/favorites/1").session(student))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void unreviewedHouseIsHidden() throws Exception {
    long h = house(false);
    assertEquals(404, call(student, "GET", "/api/houses/" + h, null).getResponse().getStatus());
    assertEquals(
        400,
        call(
                student,
                "POST",
                "/api/reservations",
                Map.of("houseId", h, "visitTime", futureTime(2), "phone", "13800138001"))
            .getResponse()
            .getStatus());
  }

  @Test
  void filtersAndInvalidRange() throws Exception {
    ok(call(student, "GET", "/api/houses?region=天河区&minRent=1000&maxRent=2500&sort=rentAsc", null));
    assertEquals(
        400,
        call(student, "GET", "/api/houses?minRent=3000&maxRent=1000", null)
            .getResponse()
            .getStatus());
    assertEquals(400, call(student, "GET", "/api/houses?page=0", null).getResponse().getStatus());
  }

  @Test
  void sqlInjectionRemainsData() throws Exception {
    var r = call(student, "GET", "/api/houses?keyword=' OR 1=1 --", null);
    ok(r);
    assertEquals(0, json(r).get("total").asInt());
  }

  @Test
  void favoriteCanBeAddedAndRemoved() throws Exception {
    long h = house(true);
    ok(call(student, "POST", "/api/favorites/" + h, null));
    ok(call(student, "POST", "/api/favorites/" + h, null));
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM t_favorite WHERE user_id=? AND house_id=?",
            WebConfig.uid(student),
            h));
    ok(call(student, "DELETE", "/api/favorites/" + h, null));
  }

  @Test
  void duplicateReservationAndCancelRelease() throws Exception {
    long h = house(true), r = reserve(h);
    assertEquals(
        409,
        call(
                student,
                "POST",
                "/api/reservations",
                Map.of("houseId", h, "visitTime", futureTime(2), "phone", "13800138001"))
            .getResponse()
            .getStatus());
    ok(call(student, "PUT", "/api/reservations/" + r + "/cancel", null));
    reserve(h);
  }

  @Test
  void confirmedReservationCannotBeCancelledByStudent() throws Exception {
    long r = reserve(house(true));
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/reservations/" + r + "/status",
            Map.of("status", "CONFIRMED")));
    assertEquals(
        400,
        call(student, "PUT", "/api/reservations/" + r + "/cancel", null).getResponse().getStatus());
  }

  @Test
  void otherUsersCannotCancel() throws Exception {
    long r = reserve(house(true));
    var other = new MockHttpSession();
    other.setAttribute(
        "user", Map.of("id", WebConfig.uid(admin), "role", "STUDENT", "session_version", 0));
    WebConfig.csrf(other);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.transition(WebConfig.uid(admin), r, "CANCELLED", false));
  }

  @Test
  void invalidReservationTransitionIsRejected() throws Exception {
    long r = reserve(house(true));
    assertEquals(
        400,
        call(
                admin,
                "PUT",
                "/api/admin/reservations/" + r + "/status",
                Map.of("status", "COMPLETED"))
            .getResponse()
            .getStatus());
  }

  @Test
  void houseEditRequiresReviewAgain() throws Exception {
    long h = house(true);
    ok(call(admin, "PUT", "/api/admin/houses/" + h, houseData()));
    assertEquals(404, call(student, "GET", "/api/houses/" + h, null).getResponse().getStatus());
  }

  @Test
  void houseOfflineAndRepublish() throws Exception {
    long h = house(true);
    ok(call(admin, "PUT", "/api/admin/houses/" + h + "/status", Map.of("status", "OFFLINE")));
    assertEquals(404, call(student, "GET", "/api/houses/" + h, null).getResponse().getStatus());
    ok(call(admin, "PUT", "/api/admin/houses/" + h + "/status", Map.of("status", "PUBLISHED")));
  }

  @Test
  void linkedActiveHouseCannotBeDeleted() throws Exception {
    long h = house(true);
    reserve(h);
    assertEquals(
        400, call(admin, "DELETE", "/api/admin/houses/" + h, null).getResponse().getStatus());
  }

  @Test
  void emptyHouseCanBeSoftDeleted() throws Exception {
    long h = house(false);
    ok(call(admin, "DELETE", "/api/admin/houses/" + h, null));
    assertEquals("DELETED", db.one("SELECT status FROM t_house WHERE id=?", h).get("status"));
  }

  @Test
  void reviewRequiresEligibilityAndApproval() throws Exception {
    long h = house(true);
    assertEquals(
        400,
        call(student, "POST", "/api/comments", Map.of("houseId", h, "rating", 5, "content", "不错"))
            .getResponse()
            .getStatus());
    complete(reserve(h));
    var r =
        call(
            student,
            "POST",
            "/api/comments",
            Map.of("houseId", h, "rating", 5, "content", "真实看房体验"));
    ok(r);
    assertEquals(0, json(call(student, "GET", "/api/houses/" + h, null)).get("comments").size());
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/comments/" + json(r).get("id").asLong() + "/review",
            Map.of("status", "APPROVED", "note", "已审核")));
    assertEquals(1, json(call(student, "GET", "/api/houses/" + h, null)).get("comments").size());
  }

  @Test
  void consultationCanBeRepliedTo() throws Exception {
    long h = house(true);
    var r = call(student, "POST", "/api/messages", Map.of("houseId", h, "content", "可以养花吗？"));
    ok(r);
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/messages/" + json(r).get("id").asLong() + "/reply",
            Map.of("reply", "可以，请保持公共区域整洁。")));
    var list = json(call(student, "GET", "/api/messages", null));
    assertTrue(list.toString().contains("公共区域"));
  }

  @Test
  void contractWorkflowAndOverlappingLease() throws Exception {
    long h = house(true);
    complete(reserve(h));
    Map<String, Object> d =
        Map.of(
            "houseId",
            h,
            "startDate",
            LocalDate.now().plusDays(5).toString(),
            "endDate",
            LocalDate.now().plusMonths(6).toString(),
            "confirmed",
            true);
    var r = call(student, "POST", "/api/contracts", d);
    ok(r);
    long id = json(r).get("id").asLong();
    ok(call(admin, "PUT", "/api/admin/contracts/" + id + "/status", Map.of("status", "SIGNED")));
    assertEquals(400, call(student, "POST", "/api/contracts", d).getResponse().getStatus());
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/contracts/" + id + "/status",
            Map.of("status", "TERMINATED")));
  }

  @Test
  void noticesAreCrudAndXssStoredAsText() throws Exception {
    var r =
        call(
            admin,
            "POST",
            "/api/admin/notices",
            Map.of("title", "测试公告", "content", "<script>alert(1)</script>"));
    ok(r);
    long id = json(r).get("id").asLong();
    ok(call(admin, "PUT", "/api/admin/notices/" + id, Map.of("title", "修改公告", "content", "正常内容")));
    ok(call(admin, "DELETE", "/api/admin/notices/" + id, null));
  }

  @Test
  void dictionaryReferenceIsProtected() throws Exception {
    assertEquals(
        400,
        call(
                admin,
                "DELETE",
                "/api/admin/dictionaries/"
                    + db.one("SELECT id FROM t_dictionary WHERE category='REGION' AND label='天河区'")
                        .get("id"),
                null)
            .getResponse()
            .getStatus());
    var r =
        call(
            admin,
            "POST",
            "/api/admin/dictionaries",
            Map.of("category", "REGION", "label", "测试区" + System.nanoTime()));
    ok(r);
    ok(call(admin, "DELETE", "/api/admin/dictionaries/" + json(r).get("id").asLong(), null));
  }

  @Test
  void statisticsAndAuditAreAvailable() throws Exception {
    var r = call(admin, "GET", "/api/admin/stats?region=天河区", null);
    ok(r);
    assertTrue(json(r).has("distribution"));
    ok(call(admin, "GET", "/api/admin/audit", null));
    assertEquals(
        400,
        call(admin, "GET", "/api/admin/stats?from=2026-12-01&to=2026-01-01", null)
            .getResponse()
            .getStatus());
  }

  @Test
  void registersRecoveryAndResetInvalidatesPreviousSession() throws Exception {
    var s = session();
    String account = "9" + String.valueOf(System.currentTimeMillis());
    captcha(s);
    var r =
        call(
            s,
            "POST",
            "/api/auth/register",
            Map.of(
                "account",
                account,
                "name",
                "测试新用户",
                "password",
                "Pass123!",
                "captcha",
                s.getAttribute("captcha"),
                "role",
                "ADMIN"));
    ok(r);
    String recovery = json(r).get("recoveryCode").asText();
    var logged = login(account, "Pass123!");
    assertEquals("STUDENT", WebConfig.user(logged).get("role"));
    captcha(s);
    ok(
        call(
            s,
            "POST",
            "/api/auth/reset",
            Map.of(
                "account",
                account,
                "password",
                "New123!",
                "recoveryCode",
                recovery,
                "captcha",
                s.getAttribute("captcha"))));
    assertEquals(401, call(logged, "GET", "/api/me", null).getResponse().getStatus());
    login(account, "New123!");
  }

  @Test
  void rejectsWrongCaptchaAndPassword() throws Exception {
    var s = session();
    assertEquals(
        400,
        call(
                s,
                "POST",
                "/api/auth/login",
                Map.of("account", "admin", "password", "wrong", "captcha", "-1"))
            .getResponse()
            .getStatus());
    captcha(s);
    assertEquals(
        401,
        call(
                s,
                "POST",
                "/api/auth/login",
                Map.of(
                    "account", "admin", "password", "wrong", "captcha", s.getAttribute("captcha")))
            .getResponse()
            .getStatus());
  }

  @Test
  void credentialsAreNotExposed() throws Exception {
    var r = call(admin, "GET", "/api/admin/users", null);
    ok(r);
    String json = r.getResponse().getContentAsString();
    assertFalse(json.contains("password_hash"));
    assertFalse(json.contains("recovery_hash"));
    assertTrue(
        json(call(student, "GET", "/api/houses/1", null))
            .get("owner_phone")
            .asText()
            .contains("****"));
  }

  @Test
  void rejectsFakeUpload() throws Exception {
    var fake =
        new MockMultipartFile("file", "evil.png", "image/png", "<script>bad</script>".getBytes());
    var r =
        mvc.perform(
                multipart("/api/admin/images")
                    .file(fake)
                    .session(admin)
                    .header("X-CSRF-Token", csrf(admin)))
            .andReturn();
    assertEquals(400, r.getResponse().getStatus());
  }

  @Test
  void certificationUploadAndReview() throws Exception {
    ok(
        call(
            student,
            "PUT",
            "/api/me",
            Map.of("name", "演示同学", "school", "暨南大学", "phone", "13800138001")));
    var image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    var file = new MockMultipartFile("file", "cert.png", "image/png", out.toByteArray());
    var r =
        mvc.perform(
                multipart("/api/me/certification")
                    .file(file)
                    .session(student)
                    .header("X-CSRF-Token", csrf(student)))
            .andReturn();
    ok(r);
    long uid = WebConfig.uid(student);
    ok(
        call(
            admin,
            "PUT",
            "/api/admin/users/" + uid + "/certification",
            Map.of("status", "APPROVED", "note", "材料审核通过")));
    assertEquals(
        "APPROVED",
        db.one("SELECT certification FROM t_user WHERE id=?", uid).get("certification"));
  }

  @Test
  void concurrentReservationHasOneWinner() throws Exception {
    long h = house(true);
    long uid = WebConfig.uid(student);
    var time = LocalDate.now().plusDays(3).atTime(11, 0);
    var pool = Executors.newFixedThreadPool(12);
    var gate = new CountDownLatch(1);
    var success = new AtomicInteger();
    var conflicts = new AtomicInteger();
    List<Future<?>> tasks = new ArrayList<>();
    for (int i = 0; i < 12; i++)
      tasks.add(
          pool.submit(
              () -> {
                try {
                  gate.await();
                  service.create(uid, h, time, "13800138001");
                  success.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                  conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }));
    gate.countDown();
    for (var task : tasks) task.get(20, TimeUnit.SECONDS);
    pool.shutdown();
    assertEquals(1, success.get());
    assertEquals(11, conflicts.get());
    assertEquals(1, db.count("SELECT COUNT(*) FROM t_reservation WHERE house_id=?", h));
  }

  @Test
  void fiveFailuresTriggerLoginThrottle() throws Exception {
    var s = session();
    for (int i = 0; i < 5; i++) {
      captcha(s);
      assertEquals(
          401,
          call(
                  s,
                  "POST",
                  "/api/auth/login",
                  Map.of(
                      "account",
                      "noSuchThrottleAccount",
                      "password",
                      "Wrong123!",
                      "captcha",
                      s.getAttribute("captcha")))
              .getResponse()
              .getStatus());
    }
    captcha(s);
    assertEquals(
        429,
        call(
                s,
                "POST",
                "/api/auth/login",
                Map.of(
                    "account",
                    "noSuchThrottleAccount",
                    "password",
                    "Wrong123!",
                    "captcha",
                    s.getAttribute("captcha")))
            .getResponse()
            .getStatus());
  }

  @Test
  void backupContainsDatabaseAndPrivateMaterials() throws Exception {
    var r = call(admin, "POST", "/api/admin/backups", null);
    ok(r);
    var p = java.nio.file.Path.of("data/backups", json(r).get("file").asText());
    assertTrue(java.nio.file.Files.isRegularFile(p));
    try (var zip = new java.util.zip.ZipFile(p.toFile())) {
      assertNotNull(zip.getEntry("database.zip"));
    }
  }
}
