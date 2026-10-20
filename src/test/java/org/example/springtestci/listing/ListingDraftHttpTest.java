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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP/JWT, Flyway, JPA and JDBC tests. Set SALE_DRAFT_TEST_DB_* for PostgreSQL. */
class ListingDraftHttpTest {

  private static final String SECRET = "r01-test-only-signing-secret-at-least-32-bytes";
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static ConfigurableApplicationContext context;
  private static JdbcTemplate jdbc;
  private static String baseUrl;
  private static String schema;
  private static Path storageRoot;

  @BeforeAll
  static void start() throws Exception {
    String url = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = url != null && !url.isBlank();
    if (postgres && !url.contains("sale_ai_test"))
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    schema = postgres ? "sale_draft_r01_" + Long.toUnsignedString(System.nanoTime()) : "public";
    if (postgres) url += (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    else
      url =
          "jdbc:h2:mem:sale_draft_r01;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    storageRoot = Files.createTempDirectory("sale-draft-http-test-");
    try (var image =
        new ClassPathResource("static/images/hero-laptop-passport.png").getInputStream()) {
      Files.copy(image, storageRoot.resolve("draft-demo-front.png"));
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
            "--spring.datasource.driver-class-name="
                + (postgres ? "org.postgresql.Driver" : "org.h2.Driver"),
            "--spring.datasource.username="
                + (postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa"),
            "--spring.datasource.password="
                + (postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : ""),
            "--spring.flyway.enabled=true",
            "--spring.flyway.schemas=" + schema,
            "--spring.flyway.default-schema=" + schema,
            "--spring.flyway.locations=classpath:db/migration,classpath:db/demo/sale-draft",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.default_schema=" + schema,
            "--app.jwt.secret=" + SECRET,
            "--app.storage.local-root=" + storageRoot);
    jdbc = context.getBean(JdbcTemplate.class);
    baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @AfterAll
  static void stop() throws Exception {
    if (context != null) {
      if (!"public".equals(schema)) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
      context.close();
    }
    if (storageRoot != null) {
      Files.deleteIfExists(storageRoot.resolve("draft-demo-front.png"));
      Files.deleteIfExists(storageRoot);
    }
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM listing_draft_images");
    jdbc.update("DELETE FROM listing_drafts");
    jdbc.update("UPDATE products SET owner_id = 1 WHERE id = 101");
  }

  @Test
  void savesAnEmptyDraftWithoutPublishingOrChangingHistory() throws Exception {
    var response = save(101, "1", "{}");
    assertEquals(201, response.statusCode());
    var data = data(response);
    assertTrue(data.get("title").isNull());
    assertTrue(data.get("description").isNull());
    assertTrue(data.get("price").isNull());
    assertTrue(data.get("conditionGrade").isNull());
    assertEquals(0, data.get("selectedImageIds").size());
    assertEquals(0, data.get("version").asLong());
    assertNotNull(data.get("updatedAt").asText());
    assertEquals(
        "PRIVATE", jdbc.queryForObject("SELECT visibility FROM listing_drafts", String.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM listings", Integer.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM product_events WHERE visibility = 'PRIVATE'", Integer.class));
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
  }

  @Test
  void acceptsTheAccessTokenFromAnHttpOnlyCookieOnlyWithAMatchingCsrfToken() throws Exception {
    String path = "/api/v1/products/101/listing-draft";
    String cookie = "access_token=" + token("1") + "; XSRF-TOKEN=csrf-value";

    assertEquals(403, sendWithCookie(path, cookie, null).statusCode());
    assertEquals(403, sendWithCookie(path, cookie, "other-value").statusCode());
    assertEquals(201, sendWithCookie(path, cookie, "csrf-value").statusCode());
  }

  @Test
  void authEndpointsAlsoRequireACsrfTokenForStateChangingRequests() throws Exception {
    String cookie = "XSRF-TOKEN=csrf-value";

    assertEquals(403, sendWithCookie("/api/auth/login", cookie, null).statusCode());
    assertEquals(403, sendWithCookie("/api/auth/refresh", cookie, "other-value").statusCode());
    assertEquals(403, sendWithCookie("/api/auth/logout", cookie, null).statusCode());
    // CSRF를 통과한 요청만 컨트롤러에 도달한다(빈 본문이라 검증 오류 400).
    assertEquals(400, sendWithCookie("/api/auth/login", cookie, "csrf-value").statusCode());

    var logout = sendWithCookie("/api/auth/logout", cookie, "csrf-value");
    assertEquals(204, logout.statusCode());
    var cleared = logout.headers().allValues("Set-Cookie");
    assertTrue(cleared.stream().anyMatch(value -> value.startsWith("access_token=;")));
    assertTrue(cleared.stream().anyMatch(value -> value.startsWith("refresh_token=;")));
  }

  @Test
  void issuesTheCsrfCookieWhenAPageIsFirstLoaded() throws Exception {
    var response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertTrue(
        response.headers().allValues("Set-Cookie").stream()
            .anyMatch(value -> value.startsWith("XSRF-TOKEN=")));
  }

  @Test
  void savesContentAndSelectedPhotoOrderWithTheFirstAsRepresentative() throws Exception {
    var response =
        save(
            101,
            "1",
            """
        {"title":"내 노트북", "description":"배터리 교체 기록을 확인해 주세요.",
         "price":2150000,"conditionGrade":4,"selectedImageIds":[303,301,302]}
        """);
    assertEquals(201, response.statusCode());
    assertEquals("내 노트북", data(response).get("title").asText());
    assertEquals(2150000, data(response).get("price").asLong());
    assertEquals(
        List.of(303L, 301L, 302L),
        jdbc.queryForList(
            "SELECT image_id FROM listing_draft_images ORDER BY sort_order", Long.class));
    assertEquals(303, data(response).get("selectedImageIds").get(0).asLong());
  }

  @Test
  void normalizesBlankOptionalTextToNull() throws Exception {
    var response =
        save(101, "1", "{\"title\":\"  \",\"description\":\" \",\"selectedImageIds\":null}");
    assertEquals(201, response.statusCode());
    assertTrue(data(response).get("title").isNull());
    assertTrue(data(response).get("description").isNull());
    assertTrue(data(response).get("selectedImageIds").isArray());
  }

  @Test
  void rejectsNonPositiveFractionalAndOversizedPrices() throws Exception {
    for (String price : List.of("0", "-1", "1.5", "1000000000000")) {
      assertEquals(400, save(101, "1", "{\"price\":" + price + "}").statusCode(), price);
    }
    assertEquals(0, countDrafts());
  }

  @Test
  void rejectsGradesOutsideOneThroughFive() throws Exception {
    assertEquals(400, save(101, "1", "{\"conditionGrade\":0}").statusCode());
    assertEquals(400, save(101, "1", "{\"conditionGrade\":6}").statusCode());
    assertEquals(201, save(101, "1", "{\"conditionGrade\":1}").statusCode());
  }

  @Test
  void rejectsTextBeyondTheScreenLimits() throws Exception {
    assertEquals(
        400, save(101, "1", JSON.writeValueAsString(Map.of("title", "a".repeat(61)))).statusCode());
    assertEquals(
        400,
        save(101, "1", JSON.writeValueAsString(Map.of("description", "a".repeat(2001))))
            .statusCode());
  }

  @Test
  void rejectsForeignDeletedAndNonProductPhotos() throws Exception {
    for (long image : List.of(304L, 401L, 399L, 999L)) {
      assertEquals(400, save(101, "1", "{\"selectedImageIds\":[" + image + "]}").statusCode());
    }
    assertEquals(0, countDrafts());
  }

  @Test
  void rejectsDuplicateNullAndOversizedPhotoSelections() throws Exception {
    assertEquals(400, save(101, "1", "{\"selectedImageIds\":[301,301]}").statusCode());
    assertEquals(400, save(101, "1", "{\"selectedImageIds\":[null]}").statusCode());
    assertEquals(400, save(101, "1", "{\"selectedImageIds\":[0]}").statusCode());
    assertEquals(
        400,
        save(
                101,
                "1",
                JSON.writeValueAsString(
                    Map.of("selectedImageIds", java.util.Collections.nCopies(21, 301))))
            .statusCode());
  }

  @Test
  void requiresAuthenticationAndTheCurrentOwner() throws Exception {
    // CSRF 토큰은 맞지만 로그인하지 않은 요청
    assertEquals(
        401,
        sendWithCookie("/api/v1/products/101/listing-draft", "XSRF-TOKEN=csrf-value", "csrf-value")
            .statusCode());
    // CSRF 토큰도 없는 비로그인 쓰기 요청은 CSRF 검사에서 먼저 거부된다.
    assertEquals(403, save(101, null, "{}").statusCode());
    assertEquals(404, save(101, "2", "{}").statusCode());
    assertEquals(404, save(103, "1", "{}").statusCode());
    assertEquals(404, save(999, "1", "{}").statusCode());
    assertEquals(0, countDrafts());
  }

  @Test
  void derivesAuthorAndPrivacyFromTheServerEvenIfTheClientSuppliesThem() throws Exception {
    var response =
        save(
            101,
            "1",
            "{\"authorId\":2,\"productId\":201,\"visibility\":\"PUBLIC\",\"status\":\"ACTIVE\"}");
    assertEquals(201, response.statusCode());
    assertEquals(1, jdbc.queryForObject("SELECT author_id FROM listing_drafts", Long.class));
    assertEquals(101, jdbc.queryForObject("SELECT product_id FROM listing_drafts", Long.class));
    assertEquals(
        "PRIVATE", jdbc.queryForObject("SELECT visibility FROM listing_drafts", String.class));
  }

  @Test
  void returnsOnlyOwnedUnarchivedProductsAndSafeComposition() throws Exception {
    var list = send("GET", "/api/v1/listing-draft-products", "1", null);
    assertEquals(200, list.statusCode());
    assertEquals(2, data(list).size());
    var composition = send("GET", "/api/v1/products/101/listing-draft/composition", "1", null);
    assertEquals(3, data(composition).get("images").size());
    assertEquals(18, data(composition).get("specifications").get("ramGb").asInt());
    for (String field : List.of("ownerId", "serialNumber", "storageKey", "documents"))
      assertFalse(composition.body().contains(field));
    assertEquals(
        404, send("GET", "/api/v1/products/101/listing-draft/composition", "2", null).statusCode());
  }

  @Test
  void updatesTheSameDraftUsingItsReturnedVersion() throws Exception {
    var first = save(101, "1", "{\"selectedImageIds\":[301,302]}");
    var update =
        save(101, "1", "{\"version\":0,\"title\":\"수정한 제목\",\"selectedImageIds\":[303,301]}");
    assertEquals(200, update.statusCode());
    assertEquals(data(first).get("draftId").asLong(), data(update).get("draftId").asLong());
    assertEquals(1, data(update).get("version").asLong());
    assertEquals(1, countDrafts());
    assertEquals(
        List.of(303L, 301L),
        jdbc.queryForList(
            "SELECT image_id FROM listing_draft_images ORDER BY sort_order", Long.class));
  }

  @Test
  void refusesMissingAndStaleVersionsWithoutLosingSavedContent() throws Exception {
    save(101, "1", "{\"title\":\"처음 제목\"}");
    assertEquals(409, save(101, "1", "{\"title\":\"버전 없음\"}").statusCode());
    save(101, "1", "{\"version\":0,\"title\":\"최신 제목\"}");
    assertEquals(409, save(101, "1", "{\"version\":0,\"title\":\"오래된 제목\"}").statusCode());
    assertEquals("최신 제목", jdbc.queryForObject("SELECT title FROM listing_drafts", String.class));
  }

  @Test
  void serializesConcurrentCreatesWithOneDraftAndOneConflict() throws Exception {
    var first = CompletableFuture.supplyAsync(() -> uncheckedSave("{}"));
    var second = CompletableFuture.supplyAsync(() -> uncheckedSave("{}"));
    var statuses = List.of(first.join(), second.join()).stream().sorted().toList();
    assertEquals(List.of(201, 409), statuses);
    assertEquals(1, countDrafts());
  }

  @Test
  void serializesConcurrentEditsUsingTheSameVersion() throws Exception {
    save(101, "1", "{}");
    var first =
        CompletableFuture.supplyAsync(() -> uncheckedSave("{\"version\":0,\"title\":\"첫번째\"}"));
    var second =
        CompletableFuture.supplyAsync(() -> uncheckedSave("{\"version\":0,\"title\":\"두번째\"}"));
    assertEquals(
        List.of(200, 409), List.of(first.join(), second.join()).stream().sorted().toList());
    assertEquals(1, jdbc.queryForObject("SELECT version FROM listing_drafts", Long.class));
  }

  @Test
  void rechecksOwnershipAfterTransferAndDoesNotExposeTheOldOwnersDraft() throws Exception {
    save(101, "1", "{\"title\":\"비공개 작성 내용\"}");
    jdbc.update("UPDATE products SET owner_id = 2 WHERE id = 101");
    assertEquals(404, save(101, "1", "{\"version\":0}").statusCode());
    assertEquals(
        404, send("GET", "/api/v1/products/101/listing-draft/images/301", "1", null).statusCode());
    assertEquals(409, save(101, "2", "{}").statusCode());
    assertEquals(405, send("GET", "/api/v1/products/101/listing-draft", "2", null).statusCode());
  }

  @Test
  void servesProductImagesWithOwnershipChecksAndWithoutCaching() throws Exception {
    var response = send("GET", "/api/v1/products/101/listing-draft/images/301", "1", null);
    assertEquals(200, response.statusCode());
    assertEquals("image/png", response.headers().firstValue("Content-Type").orElseThrow());
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(
        404, send("GET", "/api/v1/products/101/listing-draft/images/301", "2", null).statusCode());
    assertEquals(
        404, send("GET", "/api/v1/products/101/listing-draft/images/401", "1", null).statusCode());
    assertEquals(
        401, send("GET", "/api/v1/products/101/listing-draft/images/301", null, null).statusCode());
  }

  @Test
  void databaseAlsoPreventsAPublicDraft() throws Exception {
    save(101, "1", "{}");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE listing_drafts SET visibility = 'PUBLIC'"));
    assertEquals(
        "PRIVATE", jdbc.queryForObject("SELECT visibility FROM listing_drafts", String.class));
  }

  private static int countDrafts() {
    return jdbc.queryForObject("SELECT count(*) FROM listing_drafts", Integer.class);
  }

  private static JsonNode data(HttpResponse<String> response) {
    return JSON.readTree(response.body()).get("data");
  }

  private static HttpResponse<String> save(long productId, String user, String body)
      throws Exception {
    return send("POST", "/api/v1/products/" + productId + "/listing-draft", user, body);
  }

  private static int uncheckedSave(String body) {
    try {
      return save(101, "1", body).statusCode();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private static HttpResponse<String> send(String method, String path, String user, String body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15));
    if (user != null) request.header("Authorization", "Bearer " + token(user));
    if (body != null) request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> sendWithCookie(String path, String cookie, String csrfHeader)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie)
            .header("Content-Type", "application/json");
    if (csrfHeader != null) request.header("X-XSRF-TOKEN", csrfHeader);
    request.POST(HttpRequest.BodyPublishers.ofString("{}"));
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String token(String subject) throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String header =
        encoder.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString(
            ("{\"sub\":\""
                    + subject
                    + "\",\"exp\":"
                    + (Instant.now().getEpochSecond() + 3600)
                    + "}")
                .getBytes(StandardCharsets.UTF_8));
    String content = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return content
        + "."
        + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
  }
}
