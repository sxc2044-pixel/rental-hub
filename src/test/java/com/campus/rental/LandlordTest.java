package com.campus.rental;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@ActiveProfiles("demo")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:landlord;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "rental.seed-demo=true",
      "rental.upload-dir=./target/landlord-uploads"
    })
@AutoConfigureMockMvc
class LandlordTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired Store db;
  MockHttpSession landlord, tenant, other, admin;

  MockHttpSession session(long id, String role) {
    var s = new MockHttpSession();
    s.setAttribute(
        "user",
        db.one("SELECT id,account,name,role,enabled,session_version FROM t_user WHERE id=?", id));
    WebConfig.csrf(s);
    return s;
  }

  long user(String role) {
    String k = UUID.randomUUID().toString().replace("-", "");
    return db.insert(
        "INSERT INTO t_user(account,password_hash,recovery_hash,name,role,phone)"
            + " VALUES(?,?,?,?,?,'13800138000')",
        k,
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Test123!"),
        "hash",
        role,
        role);
  }

  @BeforeEach
  void setup() {
    landlord = session(user("LANDLORD"), "LANDLORD");
    tenant = session(user("STUDENT"), "STUDENT");
    other = session(user("LANDLORD"), "LANDLORD");
    admin = session(Store.id(db.one("SELECT id FROM t_user WHERE role='ADMIN' LIMIT 1")), "ADMIN");
  }

  MvcResult req(MockHttpSession s, String method, String path, Object body) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post(path);
          case "PUT" -> put(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    b.session(s).header("X-CSRF-Token", WebConfig.csrf(s));
    if (body != null) b.contentType("application/json").content(json.writeValueAsBytes(body));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    var r = req(s, m, p, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  Map<String, Object> fields() {
    var m = new HashMap<String, Object>();
    m.putAll(
        Map.of(
            "title",
            "房东测试房源",
            "region",
            "天河区",
            "layout",
            "一室一厅",
            "rent",
            1800,
            "area",
            40,
            "address",
            "测试路一号",
            "facilities",
            "空调 / 冰箱",
            "description",
            "测试房源介绍",
            "image",
            "/images/home-1.jpg",
            "owner_name",
            "测试房东"));
    m.put("owner_phone", "13800138000");
    return m;
  }

  long house() throws Exception {
    return ok(landlord, "POST", "/api/rental/houses", fields()).get("id").asLong();
  }

  void publish(long h) throws Exception {
    ok(
        admin,
        "PUT",
        "/api/admin/houses/" + h + "/review",
        Map.of("status", "PUBLISHED", "note", "材料核验通过"));
  }

  long application(long h) throws Exception {
    return ok(
            tenant,
            "POST",
            "/api/rental/applications",
            Map.of(
                "houseId",
                h,
                "startDate",
                LocalDate.now().toString(),
                "endDate",
                LocalDate.now().plusMonths(6).toString(),
                "note",
                "希望租赁半年"))
        .get("id")
        .asLong();
  }

  Map<String,Object> signature(long c, MockHttpSession session) {
    return new HashMap<>(Map.of("termsHash", ContractText.hash(db.one("SELECT terms FROM t_contract WHERE id=?", c).get("terms")),
        "consent", true, "signerName", WebConfig.user(session).get("name"), "password", "Test123!"));
  }

  long signed() throws Exception {
    long h = house();
    publish(h);
    long a = application(h);
    ok(
        landlord,
        "PUT",
        "/api/rental/applications/" + a + "/review",
        Map.of("status", "APPROVED", "note", "同意"));
    long c =
        ((Number) db.one("SELECT contract_id FROM t_application WHERE id=?", a).get("contract_id"))
            .longValue();
    ok(landlord, "PUT", "/api/rental/contracts/" + c + "/sign", signature(c, landlord) );
    ok(tenant, "PUT", "/api/rental/contracts/" + c + "/sign", signature(c, tenant) );
    return c;
  }

  @Test
  void ownerIsolationAndReviewGate() throws Exception {
    long h = house();
    assertEquals(
        404, req(other, "PUT", "/api/rental/houses/" + h, fields()).getResponse().getStatus());
    assertEquals(
        400,
        req(landlord, "PUT", "/api/rental/houses/" + h + "/status", Map.of("status", "PUBLISHED"))
            .getResponse()
            .getStatus());
    publish(h);
    ok(landlord, "PUT", "/api/rental/houses/" + h, fields());
    assertEquals("PENDING", db.one("SELECT status FROM t_house WHERE id=?", h).get("status"));
    assertEquals(403, req(landlord, "GET", "/api/admin/houses", null).getResponse().getStatus());
    assertEquals(
        403,
        req(landlord, "POST", "/api/messages", Map.of("houseId", h, "content", "x"))
            .getResponse()
            .getStatus());
  }

  @Test
  void approvalCreatesOneContractAndBothSignaturesRequired() throws Exception {
    long h = house();
    publish(h);
    long a = application(h);
    ok(
        landlord,
        "PUT",
        "/api/rental/applications/" + a + "/review",
        Map.of("status", "APPROVED", "note", "同意"));
    assertEquals(
        400,
        req(
                landlord,
                "PUT",
                "/api/rental/applications/" + a + "/review",
                Map.of("status", "APPROVED", "note", "再次同意"))
            .getResponse()
            .getStatus());
    long c =
        ((Number) db.one("SELECT contract_id FROM t_application WHERE id=?", a).get("contract_id"))
            .longValue();
    String path = "/api/rental/contracts/" + c + "/sign";
    for (String key : List.of("consent", "termsHash", "signerName", "password")) {
      var bad = signature(c, tenant);
      bad.put(key, key.equals("consent") ? false : key.equals("termsHash") ? "0".repeat(64) : "错误");
      assertEquals(400, req(tenant, "PUT", path, bad).getResponse().getStatus());
      assertEquals(false, db.one("SELECT tenant_signed FROM t_contract WHERE id=?", c).get("tenant_signed"));
    }
    ok(tenant, "PUT", "/api/rental/contracts/" + c + "/sign", signature(c, tenant));
    assertEquals("DRAFT", db.one("SELECT status FROM t_contract WHERE id=?", c).get("status"));
    ok(landlord, "PUT", "/api/rental/contracts/" + c + "/sign", signature(c, landlord));
    assertEquals("SIGNED", db.one("SELECT status FROM t_contract WHERE id=?", c).get("status"));
    var signedRow = db.one("SELECT * FROM t_contract WHERE id=?", c);
    assertEquals(ContractText.hash(signedRow.get("terms")), signedRow.get("tenant_signed_hash"));
    assertNotNull(signedRow.get("landlord_signed_time"));
    assertTrue(signedRow.get("terms").toString().contains("押金"));
    assertEquals(400, req(tenant, "PUT", path, signature(c, tenant)).getResponse().getStatus());

    assertEquals(
        404,
        req(other, "PUT", "/api/rental/contracts/" + c + "/sign", signature(c, other)).getResponse().getStatus());
    assertEquals(
        400, req(landlord, "DELETE", "/api/rental/houses/" + h, null).getResponse().getStatus());
  }

  @Test
  void billsRepairsAndMoveoutCompleteLifecycle() throws Exception {
    long c = signed();
    var body =
        Map.of(
            "contractId",
            c,
            "month",
            YearMonth.now().toString(),
            "dueDate",
            LocalDate.now().plusDays(5).toString(),
            "amount",
            1800,
            "note",
            "月租");
    ok(landlord, "POST", "/api/rental/bills", body);
    assertEquals(400, req(landlord, "POST", "/api/rental/bills", body).getResponse().getStatus());
    long b = Store.id(db.one("SELECT id FROM t_bill WHERE contract_id=?", c));
    ok(tenant, "POST", "/api/rental/moveouts", Map.of("contractId", c, "reason", "毕业退租"));
    long m = Store.id(db.one("SELECT id FROM t_moveout WHERE contract_id=?", c));
    assertEquals(
        400,
        req(
                landlord,
                "PUT",
                "/api/rental/moveouts/" + m + "/review",
                Map.of("status", "APPROVED", "note", "同意"))
            .getResponse()
            .getStatus());
    assertEquals(
        400,
        req(landlord, "POST", "/api/rental/bills/batch-delete", Map.of("ids", List.of(b)))
            .getResponse()
            .getStatus());
    assertEquals(
        404,
        req(other, "PUT", "/api/rental/bills/" + b + "/settle", null).getResponse().getStatus());
    ok(landlord, "PUT", "/api/rental/bills/" + b + "/settle", null);
    ok(tenant, "POST", "/api/rental/repairs", Map.of("contractId", c, "description", "空调漏水"));
    long r = Store.id(db.one("SELECT id FROM t_repair WHERE contract_id=?", c));
    assertEquals(
        400,
        req(
                landlord,
                "PUT",
                "/api/rental/repairs/" + r,
                Map.of(
                    "status",
                    "COMPLETED",
                    "worker",
                    "张师傅",
                    "cost",
                    100,
                    "responsibility",
                    "LANDLORD",
                    "note",
                    "已修复"))
            .getResponse()
            .getStatus());
    for (String status : List.of("ASSIGNED", "COMPLETED"))
      ok(
          landlord,
          "PUT",
          "/api/rental/repairs/" + r,
          Map.of(
              "status",
              status,
              "worker",
              "张师傅 13800138000",
              "cost",
              100,
              "responsibility",
              "LANDLORD",
              "note",
              "已核验"));
    ok(landlord, "POST", "/api/rental/bills/batch-delete", Map.of("ids", List.of(b)));
    assertTrue(ok(landlord, "GET", "/api/rental/dashboard", null).get("income").asDouble() >= 1800);
    ok(
        landlord,
        "PUT",
        "/api/rental/moveouts/" + m + "/review",
        Map.of("status", "APPROVED", "note", "费用已结清，同意退租"));
    assertEquals("TERMINATED", db.one("SELECT status FROM t_contract WHERE id=?", c).get("status"));
  }

  @Test
  void overlappingApprovalsRejected() throws Exception {
    long h = house();
    publish(h);
    long a = application(h);
    ok(
        landlord,
        "PUT",
        "/api/rental/applications/" + a + "/review",
        Map.of("status", "APPROVED", "note", "同意"));
    long second = application(h);
    assertEquals(
        400,
        req(
                landlord,
                "PUT",
                "/api/rental/applications/" + second + "/review",
                Map.of("status", "APPROVED", "note", "同意"))
            .getResponse()
            .getStatus());
  }

  @Test
  void batchDeletionRollsBackAsWhole() throws Exception {
    long a = house(), b = house();
    ok(landlord, "POST", "/api/rental/houses/batch-delete", Map.of("ids", List.of(a, b)));
    long c = house();
    long foreign = ok(other, "POST", "/api/rental/houses", fields()).get("id").asLong();
    assertEquals(
        404,
        req(landlord, "POST", "/api/rental/houses/batch-delete", Map.of("ids", List.of(c, foreign)))
            .getResponse()
            .getStatus());
    assertEquals("PENDING", db.one("SELECT status FROM t_house WHERE id=?", c).get("status"));
  }

  @Test
  void chatTypesUnreadAndPrivacy() throws Exception {
    long h = house();
    publish(h);
    long peer = WebConfig.uid(landlord);
    ok(tenant, "POST", "/api/rental/chat/" + peer, Map.of("type", "TEXT", "content", "你好，想了解房源"));
    long id =
        ok(
                tenant,
                "POST",
                "/api/rental/chat/" + peer,
                Map.of("type", "HOUSE", "content", "", "houseId", h))
            .get("id")
            .asLong();
    assertEquals(2, ok(landlord, "GET", "/api/rental/unread", null).get("count").asInt());
    assertEquals(2, ok(landlord, "GET", "/api/rental/chat/" + WebConfig.uid(tenant), null).size());
    assertEquals(0, ok(other, "GET", "/api/rental/chat/" + WebConfig.uid(tenant), null).size());
    ok(
        landlord,
        "PUT",
        "/api/rental/chat/" + WebConfig.uid(tenant) + "/read",
        Map.of("through", id));
    assertEquals(0, ok(landlord, "GET", "/api/rental/unread", null).get("count").asInt());
    assertEquals(
        400,
        req(
                tenant,
                "POST",
                "/api/rental/chat/" + peer,
                Map.of("type", "IMAGE", "content", "/images/home-1.jpg"))
            .getResponse()
            .getStatus());
    var bytes = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
    var response =
        mvc.perform(
                multipart("/api/rental/images")
                    .file(file)
                    .session(tenant)
                    .header("X-CSRF-Token", WebConfig.csrf(tenant)))
            .andReturn();
    assertEquals(200, response.getResponse().getStatus());
    String url = json.readTree(response.getResponse().getContentAsString()).get("url").asText();
    ok(tenant, "POST", "/api/rental/chat/" + peer, Map.of("type", "IMAGE", "content", url));
    assertEquals(
        400,
        req(
                landlord,
                "POST",
                "/api/rental/chat/" + WebConfig.uid(tenant),
                Map.of("type", "IMAGE", "content", url))
            .getResponse()
            .getStatus());
  }

  @Test
  void registrationCannotEscalateToAdmin() throws Exception {
    var s = new MockHttpSession();
    mvc.perform(get("/api/captcha").session(s));
    WebConfig.csrf(s);
    var b =
        new HashMap<String, Object>(
            Map.of(
                "account",
                "188" + System.nanoTime() % 100000000,
                "password",
                "Landlord123!",
                "name",
                "新房东",
                "role",
                "LANDLORD",
                "captcha",
                s.getAttribute("captcha")));
    assertEquals(200, req(s, "POST", "/api/auth/register", b).getResponse().getStatus());
    mvc.perform(get("/api/captcha").session(s));
    b.put("captcha", s.getAttribute("captcha"));
    b.put("role", "ADMIN");
    b.put("account", "189" + System.nanoTime() % 100000000);
    assertEquals(400, req(s, "POST", "/api/auth/register", b).getResponse().getStatus());
  }
}
