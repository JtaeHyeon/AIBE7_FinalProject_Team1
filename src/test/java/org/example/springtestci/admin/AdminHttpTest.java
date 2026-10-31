package org.example.springtestci.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.example.springtestci.admin.repository.AdminReadRepository;
import org.example.springtestci.admin.service.*;
import org.example.springtestci.common.error.CommonException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT 필터·HTTP·DB로 관리자 접근 및 허용 정보 투영을 검사한다. 외부 AI·Redis는 호출하지 않는다. */
class AdminHttpTest {
  private static final String SECRET = "admin-http-test-only-secret-at-least-32-bytes";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static ConfigurableApplicationContext context;
  private static JdbcTemplate jdbc;
  private static String base, schema;
  private static DriverManagerDataSource schemaSource;

  @BeforeAll
  static void start() {
    String url = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = url != null && !url.isBlank();
    if (postgres && (!url.startsWith("jdbc:postgresql:") || !url.contains("sale_ai_test")))
      throw new IllegalStateException("Dedicated sale_ai_test DB required");
    schema = postgres ? "admin_http_" + Long.toUnsignedString(System.nanoTime()) : "public";
    String user = postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa";
    String password = postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : "";
    url =
        postgres
            ? url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema
            : "jdbc:h2:mem:admin_http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    if (postgres) {
      schemaSource = new DriverManagerDataSource(url, user, password);
      Flyway.configure()
          .dataSource(schemaSource)
          .schemas(schema)
          .defaultSchema(schema)
          .locations("classpath:db/migration")
          .load()
          .migrate();
      Flyway.configure()
          .dataSource(schemaSource)
          .schemas(schema)
          .defaultSchema(schema)
          .outOfOrder(true)
          .locations(
              "classpath:db/migration",
              "classpath:db/pending/trade-p2",
              "classpath:db/pending/trade-p3")
          .load()
          .migrate();
    }
    var app = new SpringApplication(SpringTestCiApplication.class);
    app.setDefaultProperties(
        Map.of("spring.main.banner-mode", "off", "logging.level.root", "WARN"));
    context =
        app.run(
            "--spring.profiles.active=test",
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.datasource.url=" + url,
            "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password,
            "--spring.datasource.driver-class-name="
                + (postgres ? "org.postgresql.Driver" : "org.h2.Driver"),
            "--spring.flyway.enabled=" + !postgres,
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.default_schema=" + schema,
            "--app.features.trade-purchase-requests.enabled=true",
            "--app.features.trade-p3.enabled=false",
            "--app.features.trade-p4.enabled=false",
            "--app.features.listing-history.enabled=false",
            "--app.ai.summary.enabled=false",
            "--app.ai.listing.api-key=",
            "--app.jwt.secret=" + SECRET);
    jdbc = context.getBean(JdbcTemplate.class);
    // H2용 조회 fixture: 부분 인덱스가 필요한 정식 P2/P3 DDL은 별도의 PostgreSQL 실행에서 검증한다.
    if (!postgres)
      jdbc.execute(
          """
        CREATE TABLE purchase_requests(id BIGINT PRIMARY KEY, room_id BIGINT, listing_id BIGINT,
          request_status VARCHAR(20), transaction_status VARCHAR(20), cancelled_by_id BIGINT,
          cancelled_at TIMESTAMP WITH TIME ZONE, cancel_reason VARCHAR(500))
        """);
    jdbc.update(
        """
        INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES
        (1,'seller@admin-test.invalid','unused','a-seller','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
        (2,'buyer@admin-test.invalid','unused','a-buyer','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
        (9,'admin@admin-test.invalid','unused','a-admin','ADMIN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """);
    new ResourceDatabasePopulator(new ClassPathResource("db/fixtures/listing-draft-products.sql"))
        .execute(context.getBean(javax.sql.DataSource.class));
    base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @AfterAll
  static void stop() {
    if (context != null) context.close();
    if (schemaSource != null && schema != null && schema.matches("admin_http_[0-9]+"))
      new JdbcTemplate(schemaSource).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }

  @BeforeEach
  void fixture() {
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = 9");
    jdbc.update("UPDATE users SET nickname = 'a-seller' WHERE id = 1");
    for (String table :
        List.of("audit_logs", "purchase_requests", "chat_messages", "chat_rooms", "listings"))
      jdbc.update("DELETE FROM " + table);
    jdbc.update(
        """
        INSERT INTO listings(id,product_id,seller_id,title,price,condition_grade,description,status,published_at,source_draft_id,source_draft_version,active_slot)
        VALUES(501,101,1,'예약 글',1000,3,'설명','RESERVED',CURRENT_TIMESTAMP,501,0,1),
        (502,102,1,'취소 글',1000,3,'설명','ACTIVE',CURRENT_TIMESTAMP,502,0,1),
        (503,201,2,'완료 글',1000,3,'설명','SOLD',CURRENT_TIMESTAMP,503,0,NULL)
        """);
    jdbc.update(
        "INSERT INTO chat_rooms(id,listing_id,seller_id,inquirer_id) VALUES(11,501,1,2),(12,502,1,2),(13,503,2,1)");
    jdbc.update(
        "INSERT INTO chat_messages(room_id,sender_id,content) VALUES(11,1,'PRIVATE_CHAT_BODY')");
    jdbc.update(
        """
        INSERT INTO purchase_requests(id,room_id,listing_id,request_status,transaction_status,cancelled_by_id,cancelled_at,cancel_reason)
        VALUES(41,11,501,'ACCEPTED','RESERVED',NULL,NULL,NULL),
        (42,12,502,'CANCELLED','CANCELLED',2,TIMESTAMP WITH TIME ZONE '2026-10-29 13:00:00+00','MUTUAL_AGREEMENT'),
        (43,13,503,'ACCEPTED','COMPLETED',NULL,NULL,NULL)
        """);
    audit(
        101,
        101,
        1L,
        41,
        "PURCHASE_REQUEST_ACCEPTED",
        "PENDING",
        "ACCEPTED",
        null,
        "2026-10-28T09:00:00Z");
    audit(
        102,
        102,
        1L,
        42,
        "PURCHASE_REQUEST_ACCEPTED",
        "PENDING",
        "ACCEPTED",
        null,
        "2026-10-29T09:00:00Z");
    audit(
        103,
        201,
        2L,
        43,
        "TRADE_COMPLETED",
        null,
        "{\"transactionStatus\":\"COMPLETED\",\"token\":\"PRIVATE_TRANSFER_TOKEN\"}",
        null,
        "2026-10-29T14:00:00Z");
    audit(
        104,
        101,
        1L,
        77,
        "HISTORY_CORRECTED",
        "{\"title\":\"점검\",\"status\":\"VALID\",\"content\":\"RAM 16GB\",\"objectKey\":\"PRIVATE_OBJECT_KEY\",\"document\":{\"ocr\":\"PRIVATE_OCR\"}}",
        "{\"title\":\"정정 점검\",\"content\":\"RAM 32GB\",\"specsAfter\":\"{\\\"ramGb\\\":32,\\\"serialNumber\\\":\\\"PRIVATE_SERIAL\\\"}\",\"messages\":[\"PRIVATE_CHAT_BODY\"]}",
        "메모리 용량 오기 정정",
        "2026-10-30T09:00:00Z");
    audit(
        105,
        101,
        null,
        77,
        "HISTORY_VOIDED",
        "{\"status\":\"VALID\"}",
        "{\"status\":\"VOID\"}",
        "중복 이력",
        "2026-10-30T09:00:00Z");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/admin",
        "/admin/",
        "/admin.html",
        "/pages/admin.html",
        "/api/v1/admin/trades",
        "/api/v1/admin/trades/41",
        "/api/v1/admin/audit-logs",
        "/api/v1/admin/audit-logs/104"
      })
  void protectsEveryEntry(String path) throws Exception {
    assertEquals(401, send(path, null, "GET").statusCode());
    assertEquals(403, send(path, jwt("1", "ADMIN"), "GET").statusCode());
  }

  @Test
  void currentDbRoleOverridesJwtScopeAndRevocationAppliesImmediately() throws Exception {
    String token = jwt("9", "USER");
    assertEquals(200, send("/admin", token, "GET").statusCode());
    jdbc.update("UPDATE users SET role = 'USER' WHERE id = 9");
    assertEquals(403, send("/api/v1/admin/audit-logs", token, "GET").statusCode());
    assertEquals(403, send("/admin.html", token, "GET").statusCode());
    assertEquals(403, send("/api/v1/admin/trades", jwt("99999", "ADMIN"), "GET").statusCode());
    assertEquals(
        403, send("/api/v1/admin/trades", jwt("not-a-number", "ADMIN"), "GET").statusCode());
  }

  @Test
  void servesProtectedHtmlWithCookieAndNoStore() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base + "/admin"))
            .header("Cookie", "access_token=" + jwt("9", "ADMIN"))
            .GET()
            .build();
    var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("거래·취소 기록"));
    assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
  }

  @Test
  void pagesAndFiltersTradesWithActualCancellationMetadata() throws Exception {
    var page = data("/api/v1/admin/trades?size=1&page=1");
    assertEquals(3, page.path("total").asInt());
    assertEquals(42, page.path("items").get(0).path("id").asInt());
    var selected =
        data(
            "/api/v1/admin/trades?status=CANCELLED&productId=102&actorId=2&from=2026-10-29&to=2026-10-29");
    assertEquals(1, selected.path("total").asInt());
    var detail = data("/api/v1/admin/trades/42");
    assertEquals("MUTUAL_AGREEMENT", detail.path("cancelReason").asText());
    assertEquals(2, detail.path("cancelledById").asInt());
    assertEquals("2026-10-29T13:00:00Z", detail.path("cancelledAt").asText());
    assertEquals("2026-10-29T09:00:00Z", detail.path("reservedAt").asText());
    assertTrue(detail.path("completedAt").isNull());
    assertEquals(
        "2026-10-29T14:00:00Z", data("/api/v1/admin/trades/43").path("completedAt").asText());
    assertFalse(detail.toString().contains("PRIVATE_"));
    assertFalse(detail.has("chatMessages"));
  }

  @Test
  void auditsShowChangesAndReasonWithoutRawPrivateFields() throws Exception {
    var response = send("/api/v1/admin/audit-logs/104", jwt("9", "ADMIN"), "GET");
    assertEquals(200, response.statusCode());
    assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    assertFalse(response.body().contains("PRIVATE_"));
    assertFalse(response.body().contains("objectKey"));
    var value = JSON.readTree(response.body()).path("data");
    assertEquals("RAM 16GB", value.path("before").path("values").path("content").asText());
    assertEquals(32, value.path("after").path("values").path("specsAfter").path("ramGb").asInt());
    assertEquals("메모리 용량 오기 정정", value.path("reason").asText());
    var logs = data("/api/v1/admin/audit-logs?productId=101&from=2026-10-30&to=2026-10-30&size=1");
    assertEquals(2, logs.path("total").asInt());
    assertEquals(105, logs.path("items").get(0).path("id").asInt());
    assertTrue(logs.path("items").get(0).path("actorId").isNull());
    assertEquals(
        1,
        data("/api/v1/admin/audit-logs?action=HISTORY_CORRECTED&actorId=1").path("total").asInt());
    assertEquals(
        "PENDING",
        data("/api/v1/admin/audit-logs/101").path("before").path("values").path("status").asText());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "size=101",
        "size=0",
        "page=-1",
        "page=100001",
        "productId=0",
        "actorId=-2",
        "from=2026-11-01&to=2026-10-30",
        "from=not-a-date",
        "action=HISTORY_VOIDED%27",
        "status=UNKNOWN"
      })
  void rejectsInvalidFilters(String query) throws Exception {
    assertEquals(
        400, send("/api/v1/admin/audit-logs?" + query, jwt("9", "ADMIN"), "GET").statusCode());
  }

  @Test
  void detailNotFoundAndEmptyPagesAreHonest() throws Exception {
    assertEquals(404, send("/api/v1/admin/trades/999", jwt("9", "ADMIN"), "GET").statusCode());
    assertEquals(404, send("/api/v1/admin/audit-logs/999", jwt("9", "ADMIN"), "GET").statusCode());
    assertEquals(0, data("/api/v1/admin/trades?productId=999").path("total").asInt());
    var page = data("/api/v1/admin/audit-logs?page=100");
    assertEquals(5, page.path("total").asInt());
    assertTrue(page.path("items").isEmpty());
  }

  @Test
  void adminRoutesHaveNoMutationEndpointsAndReadsDoNotChangeBusinessState() throws Exception {
    String before = jdbc.queryForList("SELECT * FROM purchase_requests ORDER BY id").toString();
    for (String method : List.of("POST", "PATCH", "DELETE"))
      assertEquals(405, send("/api/v1/admin/trades/41", jwt("9", "ADMIN"), method).statusCode());
    data("/api/v1/admin/trades");
    data("/api/v1/admin/trades/41");
    data("/api/v1/admin/audit-logs");
    data("/api/v1/admin/audit-logs/104");
    assertEquals(
        before, jdbc.queryForList("SELECT * FROM purchase_requests ORDER BY id").toString());
    assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE content='PRIVATE_CHAT_BODY'", Integer.class));
  }

  @Test
  void disabledTradeFeatureDoesNotAttemptMissingTables() {
    var service =
        new AdminReadService(
            context.getBean(AdminAccessService.class),
            context.getBean(AdminReadRepository.class),
            context.getBean(AdminAuditFacts.class),
            false);
    assertEquals(
        "ADMIN_001",
        assertThrows(CommonException.class, () -> service.trade(9, 41)).errorCode().code());
  }

  @Test
  void joinsCurrentProductAndMemberInformationWithoutCredentialsOrSerialData() throws Exception {
    var trade = data("/api/v1/admin/trades/41");
    assertEquals("MacBook Pro 14 M3 Pro", trade.path("product").path("title").asText());
    assertEquals("Apple", trade.path("product").path("manufacturer").asText());
    assertEquals("MacBook Pro 14", trade.path("product").path("modelName").asText());
    assertEquals("a-seller", trade.path("seller").path("nickname").asText());
    assertEquals("buyer@admin-test.invalid", trade.path("buyer").path("email").asText());
    assertEquals("예약 글", trade.path("listingTitle").asText());
    var cancelled = data("/api/v1/admin/trades/42");
    assertEquals("a-buyer", cancelled.path("cancelledBy").path("nickname").asText());
    var logs = data("/api/v1/admin/audit-logs/104").path("record");
    assertEquals("a-seller", logs.path("actor").path("nickname").asText());
    assertEquals("seller@admin-test.invalid", logs.path("actor").path("email").asText());
    assertEquals("MacBook Pro 14 M3 Pro", logs.path("product").path("title").asText());
    String payload = trade.toString() + logs;
    for (String forbidden :
        List.of(
            "passwordHash",
            "password_hash",
            "serial_number",
            "serialNumber",
            "test-ciphertext",
            "PRIVATE_")) assertFalse(payload.contains(forbidden));
    jdbc.update("UPDATE users SET nickname='a-renamed' WHERE id=1");
    assertEquals(
        "a-renamed", data("/api/v1/admin/trades/41").path("seller").path("nickname").asText());
  }

  @Test
  void searchesNamesModelsNicknamesAndEmailsAsLiteralCaseInsensitiveText() throws Exception {
    assertEquals(
        1,
        data("/api/v1/admin/trades?productQuery=macbook&memberQuery=A-BUYER")
            .path("total")
            .asInt());
    assertEquals(1, data("/api/v1/admin/trades?productQuery=LENOVO").path("total").asInt());
    assertEquals(
        3,
        data("/api/v1/admin/audit-logs?memberQuery=seller%40admin-test.invalid")
            .path("total")
            .asInt());
    for (String literal : List.of("%", "_", "' OR 1=1 --", "missing-product")) {
      String query = java.net.URLEncoder.encode(literal, StandardCharsets.UTF_8);
      assertEquals(0, data("/api/v1/admin/trades?productQuery=" + query).path("total").asInt());
      assertEquals(0, data("/api/v1/admin/audit-logs?memberQuery=" + query).path("total").asInt());
    }
  }

  @Test
  void missingActorDoesNotDropRecordsOrTurnThemIntoSystemActions() throws Exception {
    jdbc.update("UPDATE audit_logs SET actor_id=99999 WHERE id=104");
    var record = data("/api/v1/admin/audit-logs/104").path("record");
    assertEquals(99999, record.path("actorId").asInt());
    assertEquals(99999, record.path("actor").path("id").asInt());
    assertTrue(record.path("actor").path("nickname").isNull());
    assertTrue(data("/api/v1/admin/audit-logs/105").path("record").path("actor").isNull());
    assertEquals(5, data("/api/v1/admin/audit-logs").path("total").asInt());
  }

  private void audit(
      long id,
      long product,
      Long actor,
      long target,
      String action,
      String before,
      String after,
      String reason,
      String at) {
    jdbc.update(
        "INSERT INTO audit_logs(id,product_id,actor_id,target_id,action,before_facts,after_facts,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        product,
        actor,
        target,
        action,
        before,
        after,
        reason,
        java.time.OffsetDateTime.parse(at));
  }

  private JsonNode data(String path) throws Exception {
    var response = send(path, jwt("9", "ADMIN"), "GET");
    assertEquals(200, response.statusCode(), response.body());
    return JSON.readTree(response.body()).path("data");
  }

  private HttpResponse<String> send(String path, String token, String method) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(base + path));
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return HTTP.send(
        builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String jwt(String subject, String scope) throws Exception {
    var b64 = Base64.getUrlEncoder().withoutPadding();
    String header = b64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
    String body =
        b64.encodeToString(
            JSON.writeValueAsBytes(
                Map.of(
                    "sub",
                    subject,
                    "scope",
                    scope,
                    "iat",
                    Instant.now().getEpochSecond(),
                    "exp",
                    Instant.now().plusSeconds(300).getEpochSecond())));
    String signing = header + "." + body;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return signing
        + "."
        + b64.encodeToString(mac.doFinal(signing.getBytes(StandardCharsets.UTF_8)));
  }
}
