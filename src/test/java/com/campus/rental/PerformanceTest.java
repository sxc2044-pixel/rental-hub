package com.campus.rental;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** 独立内存库，避免将10万条压测数据写入演示或远程数据库。 */
@org.springframework.test.context.ActiveProfiles("demo")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:rentalperf;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "rental.seed-demo=true",
      "logging.level.root=WARN"
    })
class PerformanceTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  String url() {
    return "http://127.0.0.1:"
        + port
        + "/api/houses?region=天河区&layout=一室一厅&minRent=1000&maxRent=2500&sort=rentAsc&page=1&size=9";
  }

  void insert(int count) {
    List<Integer> numbers = new ArrayList<>();
    for (int i = 0; i < count; i++) numbers.add(i);
    jdbc.batchUpdate(
        "INSERT INTO"
            + " t_house(title,region,address,rent,area,layout,facilities,description,image,owner_name,owner_phone,status)"
            + " VALUES"
            + " (?,'天河区','压测专用地址',?,42,'一室一厅','空调','隔离压测数据','/images/room-1.svg','测试房东','13800138000','PUBLISHED')",
        numbers,
        1000,
        (ps, n) -> {
          ps.setString(1, "压测房源" + n);
          ps.setInt(2, 800 + n % 3000);
        });
  }

  double query(HttpClient client) throws Exception {
    long start = System.nanoTime();
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create(url())).timeout(Duration.ofSeconds(20)).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, r.statusCode());
    assertTrue(mapper.readTree(r.body()).get("items").size() <= 9);
    return (System.nanoTime() - start) / 1e6;
  }

  double p95(List<Double> values) {
    values.sort(Double::compare);
    return values.get((int) Math.ceil(values.size() * .95) - 1);
  }

  @Test
  void measuresHundredConcurrentUsersAtHundredThousandRows() throws Exception {
    insert(1000);
    var client = HttpClient.newHttpClient();
    for (int i = 0; i < 5; i++) query(client);
    List<Double> baseline = new ArrayList<>();
    for (int i = 0; i < 20; i++) baseline.add(query(client));
    insert(98990);
    assertEquals(100000, jdbc.queryForObject("SELECT COUNT(*) FROM t_house", Integer.class));
    for (int i = 0; i < 5; i++) query(client);
    List<Double> large = new ArrayList<>();
    for (int i = 0; i < 20; i++) large.add(query(client));
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(100);
    List<Future<Double>> jobs = new ArrayList<>();
    for (int i = 0; i < 100; i++)
      jobs.add(
          pool.submit(
              () -> {
                gate.await();
                return query(client);
              }));
    long begin = System.nanoTime();
    gate.countDown();
    List<Double> concurrent = new ArrayList<>();
    try {
      for (var job : jobs) concurrent.add(job.get(30, TimeUnit.SECONDS));
    } finally {
      pool.shutdown();
    }
    double elapsed = (System.nanoTime() - begin) / 1e6;
    var result = new LinkedHashMap<String, Object>();
    result.put("executedAt", OffsetDateTime.now().toString());
    result.put("database", "H2 in-memory / MySQL mode");
    result.put("scope", "仅本机HTTP接口与隔离内存数据库，非Kali MySQL或完整浏览器加载测试");
    result.put("baselineRows", 1010);
    result.put("largeRows", 100000);
    result.put("serialRequestsPerGroup", 20);
    result.put("baselineP95Ms", p95(baseline));
    result.put("largeP95Ms", p95(large));
    result.put("concurrentUsers", 100);
    result.put("concurrentRequests", 100);
    result.put("successfulRequests", concurrent.size());
    result.put("concurrentP95Ms", p95(concurrent));
    result.put("concurrentMaxMs", Collections.max(concurrent));
    result.put("batchElapsedMs", elapsed);
    result.put("passedAllResponses", true);
    Files.createDirectories(Path.of("docs/evidence"));
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(Path.of("docs/evidence/performance.json").toFile(), result);
  }
}
