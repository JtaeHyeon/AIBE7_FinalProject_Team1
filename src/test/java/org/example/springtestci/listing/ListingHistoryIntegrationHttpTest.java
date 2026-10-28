package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.example.springtestci.ai.service.ListingSuggestionSource;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 정식 Flyway·이력 API·조회 어댑터·JWT·HTTP를 연결한다. 외부 AI는 호출하지 않는다. */
class ListingHistoryIntegrationHttpTest {
  private static final String SECRET = "history-integration-only-jwt-secret-at-least-32-bytes";
  private static final String PUBLICATION = "/api/v1/products/101/listing-draft/publication";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static ConfigurableApplicationContext context;
  private static JdbcTemplate jdbc;
  private static String base, schema;
  private static Path files;

  @BeforeAll
  static void start() throws Exception {
    String url = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = url != null && !url.isBlank();
    if (postgres && !url.contains("sale_ai_test"))
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    schema = postgres ? "history_http_" + Long.toUnsignedString(System.nanoTime()) : "public";
    url =
        postgres
            ? url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema
            : "jdbc:h2:mem:listing_real_history;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    files = Files.createTempDirectory("listing-real-history-http-");
    var app = new SpringApplication(SpringTestCiApplication.class);
    app.setDefaultProperties(
        Map.of("spring.main.banner-mode", "off", "logging.level.root", "WARN"));
    context =
        app.run(
            "--spring.profiles.active=test",
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.datasource.url=" + url,
            "--spring.datasource.driver-class-name="
                + (postgres ? "org.postgresql.Driver" : "org.h2.Driver"),
            "--spring.datasource.username="
                + (postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa"),
            "--spring.datasource.password="
                + (postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : ""),
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.schemas=" + schema,
            "--spring.flyway.default-schema=" + schema,
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.default_schema=" + schema,
            "--app.features.listing-history.enabled=true",
            "--app.ai.summary.enabled=false",
            "--app.ai.listing.api-key=",
            "--app.jwt.secret=" + SECRET,
            "--app.storage.local-root=" + files);
    jdbc = context.getBean(JdbcTemplate.class);
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES(1,'history-owner@fixture.invalid','unused','h-owner','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'history-former@fixture.invalid','unused','h-former','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    new ResourceDatabasePopulator(new ClassPathResource("db/fixtures/listing-draft-products.sql"))
        .execute(context.getBean(javax.sql.DataSource.class));
    base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @AfterAll
  static void stop() throws Exception {
    if (context != null) {
      if (!"public".equals(schema)) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
      context.close();
    }
    if (files != null) {
      if (!files
              .toAbsolutePath()
              .normalize()
              .getParent()
              .equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
          || !files.getFileName().toString().startsWith("listing-real-history-http-"))
        throw new IllegalStateException("Unexpected test cleanup directory");
      try (var paths = Files.walk(files)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM event_documents");
    jdbc.update("DELETE FROM audit_logs");
    jdbc.update("DELETE FROM product_events");
    jdbc.update("DELETE FROM listing_images");
    jdbc.update("DELETE FROM listings");
    jdbc.update("UPDATE products SET owner_id=1,archived_at=NULL,facts_version=7 WHERE id=101");
    jdbc.update(
        "INSERT INTO listings(id,product_id,seller_id,title,price,condition_grade,description,status,published_at,source_draft_id,source_draft_version,active_slot) VALUES(11,101,1,'실제 이력 연결 검증',1200000,4,'판매 조건','ACTIVE',CURRENT_TIMESTAMP,9001,0,1)");
    event(1, 101, 2L, "PURCHASE", "2024-02-12", "VALID", null);
    event(2, 101, 1L, "INSPECTION", "2025-05-20", "VALID", null);
    event(3, 101, 2L, "REPAIR", "2025-04-02", "VOID", null);
    event(4, 101, null, "TRANSFER", "2024-06-15", "VALID", 55L);
    event(9, 201, 2L, "REPAIR", "2025-01-01", "VALID", null);
    change(101, 101, 2, "HISTORY_CORRECTED");
    change(102, 101, 2, "HISTORY_CORRECTED");
    change(103, 101, 3, "HISTORY_VOIDED");
    change(104, 101, 2, "PRODUCT_UPDATED");
    change(105, 201, 2, "HISTORY_CORRECTED");
  }

  @Test
  void ownerPreviewReadsRealPreviousOwnerVoidAndAllCorrectionMetadata() throws Exception {
    var response = get(PUBLICATION, "1");
    var history = data(response).get("history");
    assertEquals("READY", history.get("status").asString());
    assertFalse(history.get("sample").asBoolean());
    assertEquals(3, history.get("validCount").asInt());
    assertEquals(1, history.get("voidCount").asInt());
    var items = history.get("items");
    assertEquals(List.of(2L, 3L, 4L, 1L), ids(items));
    assertEquals(List.of(102L, 101L), correctionIds(items.get(0).get("corrections")));
    assertEquals("REVIEW_PENDING", items.get(0).get("detailsStatus").asString());
    assertEquals("제품 점검", items.get(0).get("title").asString());
    assertTrue(items.get(0).get("content").isNull());
    assertTrue(items.get(0).get("corrections").get(0).get("reason").isNull());
    assertEquals("VOID", items.get(1).get("status").asString());
    assertTrue(items.get(1).get("previousOwner").asBoolean());
    assertTrue(items.get(2).get("systemGenerated").asBoolean());
    assertFalse(items.get(2).get("previousOwner").asBoolean());
    assertPrivateFieldsAbsent(response.body());
  }

  @Test
  void historyApiCreatesCorrectsAndVoidsTheRecordReadByListingPublication() throws Exception {
    jdbc.update("DELETE FROM audit_logs");
    jdbc.update("DELETE FROM product_events");
    String eventsPath = "/api/v1/products/101/events";
    String creation =
        """
        {"eventType":"INSPECTION","occurredOn":"2025-05-20","title":"PRIVATE_HISTORY_TITLE",
         "content":"private-person@fixture.invalid","serviceProvider":"PRIVATE_SERVICE_PROVIDER",
         "cost":987654321,"documentIds":[]}
        """;
    assertEquals(404, write("POST", eventsPath, creation, "2").statusCode());
    var created = write("POST", eventsPath, creation, "1");
    assertEquals(201, created.statusCode(), created.body());
    long eventId = JSON.readTree(created.body()).at("/data/eventId").asLong();
    var initialResponse = get(PUBLICATION, "1");
    var history = data(initialResponse).get("history");
    assertFalse(history.get("sample").asBoolean());
    assertEquals(List.of(eventId), ids(history.get("items")));
    assertEquals("INSPECTION", history.at("/items/0/eventType").asString());
    assertPrivateFieldsAbsent(initialResponse.body());

    data(
        write(
            "PATCH",
            eventsPath + "/" + eventId,
            """
            {"occurredOn":"2025-05-21","title":"PRIVATE_CORRECTED_TITLE",
             "content":"private-person@fixture.invalid","reason":"PRIVATE_CHANGE_REASON"}
            """,
            "1"));
    var correctedResponse = get(PUBLICATION, "1");
    var corrected = data(correctedResponse).at("/history/items/0");
    assertEquals("2025-05-21", corrected.get("occurredOn").asString());
    assertEquals(1, corrected.get("corrections").size());
    assertPrivateFieldsAbsent(correctedResponse.body());

    data(
        write(
            "POST",
            eventsPath + "/" + eventId + "/void",
            "{\"reason\":\"PRIVATE_VOID_REASON\"}",
            "1"));
    var voidedResponse = get(PUBLICATION, "1");
    var voided = data(voidedResponse).get("history");
    assertEquals(0, voided.get("validCount").asInt());
    assertEquals(1, voided.get("voidCount").asInt());
    assertEquals("VOID", voided.at("/items/0/status").asString());
    assertEquals(2, voided.at("/items/0/corrections").size());
    assertPrivateFieldsAbsent(voidedResponse.body());
  }

  @Test
  void memberDetailUsesTheSameRealHistoryAndKeepsOwnershipBasedManageRights() throws Exception {
    var owner = data(get(PUBLICATION, "1")).get("history");
    var buyerResponse = get("/api/v1/listings/11", "2");
    var buyer = data(buyerResponse);
    assertEquals(owner, buyer.get("history"));
    assertFalse(buyer.get("canManage").asBoolean());
    assertTrue(data(get("/api/v1/listings/11", "1")).get("canManage").asBoolean());
    assertPrivateFieldsAbsent(buyerResponse.body());
  }

  @Test
  void aiCaptureIncludesTheWholeHistoryWithoutCallingAnExternalProvider() throws Exception {
    for (long id = 20; id < 44; id++) event(id, 101, 2L, "INSPECTION", "2025-01-01", "VALID", null);
    assertEquals(1, data(get(PUBLICATION + "?page=0&size=1", "1")).at("/history/items").size());
    var snapshot = context.getBean(ListingSuggestionSource.class).capture(101, 1);
    var value = JSON.readTree(snapshot.json());
    assertEquals(28, value.get("history").size());
    assertFalse(snapshot.sample());
    assertTrue(value.get("history").get(0).get("corrected").asBoolean());
    assertFalse(value.get("history").get(0).get("detailsReviewed").asBoolean());
    assertPrivateFieldsAbsent(snapshot.json());
    assertEquals(
        7L, jdbc.queryForObject("SELECT facts_version FROM products WHERE id=101", Long.class));
    assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class));
  }

  @Test
  void authenticationOwnershipAndEndedListingChecksStillApply() throws Exception {
    assertEquals(401, get(PUBLICATION, null).statusCode());
    assertEquals(404, get(PUBLICATION, "2").statusCode());
    assertEquals(401, get("/api/v1/listings/11", null).statusCode());
    jdbc.update("UPDATE listings SET status='SOLD',active_slot=NULL WHERE id=11");
    assertEquals(404, get("/api/v1/listings/11", "2").statusCode());
    jdbc.update("UPDATE products SET owner_id=2 WHERE id=101");
    assertEquals(404, get(PUBLICATION, "1").statusCode());
  }

  @Test
  void anActuallyEmptyHistoryIsReadyAndDifferentFromUnavailable() throws Exception {
    var result = data(get("/api/v1/products/102/listing-draft/publication", "1")).get("history");
    assertEquals("READY", result.get("status").asString());
    assertEquals(0, result.get("items").size());
    assertFalse(result.get("sample").asBoolean());
  }

  @Test
  void anUndeployedSourceDoesNotPoisonThePostgresCallingTransaction() throws Exception {
    jdbc.execute("ALTER TABLE audit_logs RENAME TO hidden_history_audit");
    try {
      var result = data(get(PUBLICATION, "1"));
      assertEquals("UNAVAILABLE", result.at("/history/status").asString());
      assertEquals("MacBook Pro 14", result.at("/product/modelName").asString());
    } finally {
      jdbc.execute("ALTER TABLE hidden_history_audit RENAME TO audit_logs");
    }
    assertEquals("READY", data(get(PUBLICATION, "1")).at("/history/status").asString());
  }

  @Test
  void jdbcReaderRequiresTheCallingServicesTransaction() {
    assertThrows(
        IllegalTransactionStateException.class,
        () -> context.getBean(ListingHistoryReader.class).read(101));
  }

  private static void event(
      long id, long product, Long author, String type, String date, String status, Long transfer) {
    jdbc.update(
        "INSERT INTO product_events(id,product_id,author_id,event_type,occurred_on,title,content,service_provider,cost,replaced_part,status,transfer_request_id,created_at) VALUES(?,?,?,?,CAST(? AS DATE),?,?,?,?,?,?,?,CAST('2026-10-01T00:00:00Z' AS TIMESTAMP WITH TIME ZONE))",
        id,
        product,
        author,
        type,
        date,
        "PRIVATE_HISTORY_TITLE",
        "private-person@fixture.invalid",
        "PRIVATE_SERVICE_PROVIDER",
        987654321L,
        "PRIVATE_REPLACED_PART",
        status,
        transfer);
  }

  private static void change(long id, long product, long event, String action) {
    jdbc.update(
        "INSERT INTO audit_logs(id,product_id,actor_id,target_id,event_id,action,reason,before_facts,after_facts,created_at) VALUES(?,?,1,?,?,?,'PRIVATE_CHANGE_REASON','PRIVATE_BEFORE_FACTS','PRIVATE_AFTER_FACTS',CAST('2026-10-02T00:00:00Z' AS TIMESTAMP WITH TIME ZONE))",
        id,
        product,
        event,
        event,
        action);
  }

  private static void assertPrivateFieldsAbsent(String value) {
    for (String forbidden :
        List.of(
            "PRIVATE_",
            "private-person@fixture.invalid",
            "987654321",
            "documentIds",
            "authorId",
            "specsBefore",
            "specsAfter",
            "\"cost\"")) assertFalse(value.contains(forbidden), forbidden);
  }

  private static List<Long> ids(JsonNode items) {
    var result = new java.util.ArrayList<Long>();
    items.forEach(value -> result.add(value.get("eventId").asLong()));
    return result;
  }

  private static List<Long> correctionIds(JsonNode items) {
    var result = new java.util.ArrayList<Long>();
    items.forEach(value -> result.add(value.get("correctionId").asLong()));
    return result;
  }

  private static JsonNode data(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    return JSON.readTree(response.body()).get("data");
  }

  private static HttpResponse<String> get(String path, String user) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).GET();
    if (user != null) request.header("Authorization", "Bearer " + token(user));
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> write(String method, String path, String body, String user)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + token(user))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String token(String user) throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String header =
        encoder.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString(
            ("{\"sub\":\"" + user + "\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}")
                .getBytes(StandardCharsets.UTF_8));
    String content = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return content
        + "."
        + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
  }
}
