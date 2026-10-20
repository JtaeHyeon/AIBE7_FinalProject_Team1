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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.example.springtestci.product.service.SerialNumberProtector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
  private static final Map<Long, Long> SAVED_DRAFT_IDS = new ConcurrentHashMap<>();

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
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.default_schema=" + schema,
            "--app.jwt.secret=" + SECRET,
            "--app.storage.local-root=" + storageRoot);
    jdbc = context.getBean(JdbcTemplate.class);
    new ResourceDatabasePopulator(new ClassPathResource("db/fixtures/listing-draft-products.sql"))
        .execute(context.getBean(javax.sql.DataSource.class));
    var serials = context.getBean(SerialNumberProtector.class);
    for (long fixtureId : List.of(101L, 102L, 103L, 201L)) {
      jdbc.update(
          "UPDATE products SET serial_number_ciphertext = ? WHERE id = ?",
          serials.encrypt("R01-TEST-" + fixtureId),
          fixtureId);
    }
    baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @AfterAll
  static void stop() throws Exception {
    if (context != null) {
      if (!"public".equals(schema)) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
      context.close();
    }
    if (storageRoot != null) {
      try (var paths = Files.walk(storageRoot)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  @BeforeEach
  void reset() {
    SAVED_DRAFT_IDS.clear();
    jdbc.update("DELETE FROM draft_images");
    jdbc.update("DELETE FROM listing_drafts");
    jdbc.update("UPDATE products SET owner_id = 1 WHERE id = 101");
  }

  @Test
  void savesAnEmptyPrivateDraft() throws Exception {
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
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
  }

  /** 쿠키 인증은 CSRF 토큰이 일치할 때만 쓰기 요청을 허용한다. */
  @Test
  void acceptsTheAccessTokenFromAnHttpOnlyCookieOnlyWithAMatchingCsrfToken() throws Exception {
    String path = "/api/v1/products/101/listing-draft";
    String cookie = "access_token=" + token("1") + "; XSRF-TOKEN=csrf-value";

    assertEquals(403, sendWithCookie(path, cookie, null).statusCode());
    assertEquals(403, sendWithCookie(path, cookie, "other-value").statusCode());
    assertEquals(201, sendWithCookie(path, cookie, "csrf-value").statusCode());
  }

  /** 인증 API의 쓰기 요청도 CSRF 토큰이 필요하다. */
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

  /** 잘못된 Access Token 쿠키는 화면 로딩을 막지 않고 API 요청에서만 거부한다. */
  @Test
  void anInvalidAccessCookieDoesNotBlockPagesButIsRejectedForApiRequests() throws Exception {
    String cookie = "access_token=not-a-valid-token";

    var page =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                .header("Cookie", cookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    var api =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/me"))
                .header("Cookie", cookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, page.statusCode());
    assertEquals(401, api.statusCode());
  }

  /** 화면을 처음 받을 때 CSRF 쿠키를 내려준다. */
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
            "SELECT product_image_id FROM draft_images ORDER BY sort_order", Long.class));
    assertEquals(303, data(response).get("selectedImageIds").get(0).asLong());
    assertEquals(
        List.of(303L),
        jdbc.queryForList(
            "SELECT product_image_id FROM draft_images WHERE is_primary = TRUE", Long.class));
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

  /** 로그인과 현재 소유자 여부를 확인한다. */
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
            "SELECT product_image_id FROM draft_images ORDER BY sort_order", Long.class));
  }

  @Test
  void photoOnlyChangesAdvanceTheDraftVersionAndPreserveTheProductPhotoOrder() throws Exception {
    save(101, "1", "{\"selectedImageIds\":[301,302]}");
    var update = save(101, "1", "{\"version\":0,\"selectedImageIds\":[302,301]}");
    assertEquals(200, update.statusCode());
    assertEquals(1, data(update).get("version").asLong());
    assertEquals(
        List.of(302L, 301L),
        jdbc.queryForList(
            "SELECT product_image_id FROM draft_images ORDER BY sort_order", Long.class));
    assertEquals(
        List.of(302L),
        jdbc.queryForList(
            "SELECT product_image_id FROM draft_images WHERE is_primary = TRUE", Long.class));
    assertEquals(
        List.of(301L, 302L, 303L),
        jdbc.queryForList(
            "SELECT id FROM product_images WHERE product_id = 101 AND deleted_at IS NULL ORDER BY sort_order",
            Long.class));
    assertEquals(409, save(101, "1", "{\"version\":0,\"selectedImageIds\":[303]}").statusCode());
  }

  @Test
  void clearingSelectedPhotosRemovesTheirRowsAndRepresentative() throws Exception {
    save(101, "1", "{\"selectedImageIds\":[301,302]}");
    var update = save(101, "1", "{\"version\":0,\"selectedImageIds\":[]}");
    assertEquals(200, update.statusCode());
    assertEquals(1, data(update).get("version").asLong());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM draft_images", Integer.class));
  }

  @Test
  void deletingASelectedProductPhotoRepairsTheDraftAndRejectsItsOldVersion() throws Exception {
    save(101, "1", "{\"selectedImageIds\":[301,303,302]}");
    try {
      var deleted = send("DELETE", "/api/v1/products/101/images/301", "1", null);
      assertEquals(200, deleted.statusCode());
      assertEquals(
          List.of(303L, 302L),
          jdbc.queryForList(
              "SELECT product_image_id FROM draft_images ORDER BY sort_order", Long.class));
      assertEquals(
          List.of(0, 1),
          jdbc.queryForList(
              "SELECT sort_order FROM draft_images ORDER BY sort_order", Integer.class));
      assertEquals(
          List.of(303L),
          jdbc.queryForList(
              "SELECT product_image_id FROM draft_images WHERE is_primary = TRUE", Long.class));
      assertEquals(1L, jdbc.queryForObject("SELECT version FROM listing_drafts", Long.class));
      assertEquals(
          409, save(101, "1", "{\"version\":0,\"selectedImageIds\":[303,302]}").statusCode());
      assertEquals(
          404,
          send("GET", "/api/v1/products/101/listing-draft/images/301", "1", null).statusCode());
    } finally {
      jdbc.update(
          "UPDATE product_images SET sort_order = sort_order + 2000000 WHERE id IN (301,302,303)");
      jdbc.update(
          "UPDATE product_images SET deleted_at = NULL, sort_order = id - 301, is_primary = FALSE WHERE id IN (301,302,303)");
      try (var image =
          new ClassPathResource("static/images/hero-laptop-passport.png").getInputStream()) {
        Files.copy(
            image,
            storageRoot.resolve("draft-demo-front.png"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  @Test
  void productRegisteredThroughTheProductApiCanBeUsedForADraft() throws Exception {
    var registered =
        send(
            "POST",
            "/api/v1/products",
            "1",
            """
        {"title":"새 제품", "manufacturer":"Lenovo", "modelName":"ThinkPad",
         "serialNumber":"R01-REAL-PRODUCT", "specifications":{
           "cpu":"Intel Core Ultra 7", "ramGb":32, "storageGb":1024, "storageType":"SSD"}}
        """);
    assertEquals(201, registered.statusCode());
    assertFalse(data(registered).get("serialNumber").asText().isBlank());
    long createdProductId = data(registered).get("productId").asLong();
    try {
      var composition =
          send(
              "GET",
              "/api/v1/products/" + createdProductId + "/listing-draft/composition",
              "1",
              null);
      assertEquals(200, composition.statusCode());
      assertEquals(data(registered).get("specifications"), data(composition).get("specifications"));
      assertEquals(0, data(composition).get("images").size());
      assertFalse(composition.body().contains("serialNumber"));
      var uploaded = uploadPhoto(createdProductId);
      assertEquals(201, uploaded.statusCode());
      long uploadedImageId = data(uploaded).get("images").get(0).get("imageId").asLong();
      var withPhoto =
          send(
              "GET",
              "/api/v1/products/" + createdProductId + "/listing-draft/composition",
              "1",
              null);
      assertEquals(uploadedImageId, data(withPhoto).get("images").get(0).get("imageId").asLong());
      assertEquals(
          200,
          send("GET", data(withPhoto).get("images").get(0).get("url").asText(), "1", null)
              .statusCode());
      var saved =
          save(
              createdProductId,
              "1",
              "{\"title\":\"판매 초안\",\"selectedImageIds\":[" + uploadedImageId + "]}");
      assertEquals(201, saved.statusCode());
      assertEquals(createdProductId, data(saved).get("productId").asLong());
      assertEquals(uploadedImageId, data(saved).get("selectedImageIds").get(0).asLong());
    } finally {
      jdbc.update("DELETE FROM listing_drafts WHERE product_id = ?", createdProductId);
      jdbc.update("DELETE FROM product_images WHERE product_id = ?", createdProductId);
      jdbc.update("DELETE FROM product_field_authors WHERE product_id = ?", createdProductId);
      jdbc.update("DELETE FROM audit_logs WHERE product_id = ?", createdProductId);
      jdbc.update("DELETE FROM laptop_details WHERE product_id = ?", createdProductId);
      jdbc.update("DELETE FROM products WHERE id = ?", createdProductId);
    }
  }

  @Test
  void databaseEnforcesPhotoReferencesUniquenessAndCascadeDeletion() throws Exception {
    var saved = save(101, "1", "{\"selectedImageIds\":[301,302]}");
    long draftId = data(saved).get("draftId").asLong();
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO draft_images (draft_id, product_image_id, sort_order, is_primary) VALUES (?, 301, 2, FALSE)",
                draftId));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO draft_images (draft_id, product_image_id, sort_order, is_primary) VALUES (?, 303, 1, FALSE)",
                draftId));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO draft_images (draft_id, product_image_id, sort_order, is_primary) VALUES (?, 999, 2, FALSE)",
                draftId));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE draft_images SET is_primary = TRUE WHERE sort_order = 1"));
    jdbc.update("DELETE FROM listing_drafts WHERE id = ?", draftId);
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM draft_images", Integer.class));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT count(*) FROM product_images WHERE product_id = 101 AND deleted_at IS NULL",
            Integer.class));
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
    assertEquals(404, send("GET", "/api/v1/products/101/listing-draft", "2", null).statusCode());
  }

  @Test
  void readsSavedContentAndPhotoOrderAfterReopeningTheProduct() throws Exception {
    var created =
        save(
            101,
            "1",
            """
        {"title":"이어서 쓸 제목", "description":"저장한 설명", "price":123456,
         "conditionGrade":4, "selectedImageIds":[303,301]}
        """);
    var reopened = readDraft(101, "1");
    assertEquals(200, reopened.statusCode());
    for (String field :
        List.of(
            "draftId",
            "productId",
            "title",
            "description",
            "price",
            "conditionGrade",
            "selectedImageIds",
            "version")) {
      assertEquals(data(created).get(field), data(reopened).get(field), field);
    }
    // DB의 timestamp(6) 저장 정밀도에 따른 나노초 반올림만 허용한다.
    assertTrue(
        Duration.between(
                    Instant.parse(data(created).get("updatedAt").asText()),
                    Instant.parse(data(reopened).get("updatedAt").asText()))
                .abs()
                .compareTo(Duration.ofNanos(1000))
            <= 0);
    assertEquals("no-store", reopened.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(404, readDraft(102, "1").statusCode());
    assertEquals(404, readDraft(101, "2").statusCode());
    assertEquals(401, readDraft(101, null).statusCode());
    var resumedSave =
        updateDraft(
            101,
            data(reopened).get("draftId").asLong(),
            "1",
            "{\"version\":0,\"title\":\"이어서 수정한 제목\",\"selectedImageIds\":[301,303]}");
    assertEquals(200, resumedSave.statusCode());
    assertEquals(1, data(readDraft(101, "1")).get("version").asLong());
    assertEquals("이어서 수정한 제목", data(readDraft(101, "1")).get("title").asText());
  }

  @Test
  void listsDraftsWithStablePaginationAndNoPrivateProductFields() throws Exception {
    var first = save(101, "1", "{\"title\":\"첫 초안\",\"description\":\"목록에 노출하지 않을 본문\"}");
    var second = save(102, "1", "{}");
    save(201, "2", "{\"title\":\"다른 사람 초안\"}");
    jdbc.update("UPDATE listing_drafts SET updated_at = created_at WHERE product_id = 101");
    jdbc.update(
        "UPDATE listing_drafts SET updated_at = (SELECT updated_at FROM listing_drafts WHERE product_id = 101) WHERE product_id = 102");
    var page0 = send("GET", "/api/v1/listing-drafts?page=0&size=1", "1", null);
    var page1 = send("GET", "/api/v1/listing-drafts?page=1&size=1", "1", null);
    assertEquals(200, page0.statusCode());
    assertEquals(2, data(page0).get("page").get("totalElements").asLong());
    assertEquals(2, data(page0).get("page").get("totalPages").asInt());
    assertTrue(data(page0).get("page").get("hasNext").asBoolean());
    assertFalse(data(page1).get("page").get("hasNext").asBoolean());
    assertEquals(
        data(second).get("draftId").asLong(),
        data(page0).get("items").get(0).get("draftId").asLong());
    assertEquals(
        data(first).get("draftId").asLong(),
        data(page1).get("items").get(0).get("draftId").asLong());
    assertTrue(data(page0).get("items").get(0).get("title").isNull());
    for (String field :
        List.of("description", "serialNumber", "ownerId", "authorId", "documents", "storageKey")) {
      assertFalse(page0.body().contains(field));
    }
    assertEquals("no-store", page0.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(
        0,
        data(send("GET", "/api/v1/listing-drafts?page=50&size=1", "1", null)).get("items").size());
  }

  @Test
  void filtersTransferredAndArchivedDraftsBeforePagination() throws Exception {
    save(101, "1", "{\"title\":\"보이지 않아야 할 초안\"}");
    save(102, "1", "{\"title\":\"내 초안\"}");
    try {
      jdbc.update("UPDATE products SET owner_id = 2 WHERE id = 101");
      assertEquals(
          1,
          data(send("GET", "/api/v1/listing-drafts?size=1", "1", null))
              .get("page")
              .get("totalElements")
              .asLong());
      assertEquals(
          102,
          data(send("GET", "/api/v1/listing-drafts?size=1", "1", null))
              .get("items")
              .get(0)
              .get("productId")
              .asLong());
      assertEquals(0, data(send("GET", "/api/v1/listing-drafts", "2", null)).get("items").size());
      jdbc.update("UPDATE products SET archived_at = CURRENT_TIMESTAMP WHERE id = 102");
      assertEquals(
          0,
          data(send("GET", "/api/v1/listing-drafts", "1", null))
              .get("page")
              .get("totalElements")
              .asLong());
      assertEquals(404, readDraft(102, "1").statusCode());
    } finally {
      jdbc.update("UPDATE products SET owner_id = 1 WHERE id = 101");
      jdbc.update("UPDATE products SET archived_at = NULL WHERE id = 102");
    }
  }

  @Test
  void validatesListPaginationAndRequiresAuthentication() throws Exception {
    assertEquals(401, send("GET", "/api/v1/listing-drafts", null, null).statusCode());
    for (String query : List.of("page=-1", "size=0", "size=101", "page=abc")) {
      assertEquals(400, send("GET", "/api/v1/listing-drafts?" + query, "1", null).statusCode());
    }
    assertEquals(0, data(send("GET", "/api/v1/listing-drafts", "1", null)).get("items").size());
  }

  @Test
  void updateRequiresAnExistingDraftItsIdentityAndVersion() throws Exception {
    assertEquals(404, updateDraft(101, 999, "1", "{\"version\":0}").statusCode());
    assertEquals(0, countDrafts());
    long id = data(save(101, "1", "{\"title\":\"보존할 제목\"}")).get("draftId").asLong();
    assertEquals(
        400,
        send("PUT", "/api/v1/products/101/listing-draft", "1", "{\"version\":0}").statusCode());
    assertEquals(409, updateDraft(101, id, "1", "{}").statusCode());
    assertEquals(409, updateDraft(101, id + 999, "1", "{\"version\":0}").statusCode());
    assertEquals(404, updateDraft(101, id, "2", "{\"version\":0}").statusCode());
    assertEquals(400, updateDraft(101, id, "1", "{\"version\":0,\"price\":-1}").statusCode());
    assertEquals("보존할 제목", data(readDraft(101, "1")).get("title").asText());
    assertEquals(
        409,
        send(
                "POST",
                "/api/v1/products/101/listing-draft",
                "1",
                "{\"version\":0,\"title\":\"POST 수정 금지\"}")
            .statusCode());
  }

  @Test
  void deletesOnlyTheDraftAndItsPhotoSelections() throws Exception {
    long id = data(save(101, "1", "{\"selectedImageIds\":[303,301]}")).get("draftId").asLong();
    assertEquals(403, deleteDraft(101, id, null, 0).statusCode());
    assertEquals(404, deleteDraft(101, id, "2", 0).statusCode());
    assertEquals(
        400,
        send("DELETE", "/api/v1/products/101/listing-draft?draftId=" + id, "1", null).statusCode());
    assertEquals(409, deleteDraft(101, id + 99, "1", 0).statusCode());
    var deleted = deleteDraft(101, id, "1", 0);
    assertEquals(204, deleted.statusCode());
    assertEquals("", deleted.body());
    assertEquals("no-store", deleted.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(0, countDrafts());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM draft_images", Integer.class));
    assertEquals(
        1, jdbc.queryForObject("SELECT count(*) FROM products WHERE id = 101", Integer.class));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT count(*) FROM product_images WHERE product_id = 101 AND deleted_at IS NULL",
            Integer.class));
    assertEquals(
        200, send("GET", "/api/v1/products/101/listing-draft/images/301", "1", null).statusCode());
    assertEquals(404, readDraft(101, "1").statusCode());
    assertEquals(404, deleteDraft(101, id, "1", 0).statusCode());
    assertEquals(201, save(101, "1", "{}").statusCode());
  }

  @Test
  void updatesAndDeletesCookieAuthenticatedDraftsOnlyWithMatchingCsrf() throws Exception {
    long id = data(save(101, "1", "{}")).get("draftId").asLong();
    String cookie = "access_token=" + token("1") + "; XSRF-TOKEN=csrf-value";
    String path = "/api/v1/products/101/listing-draft?draftId=" + id;
    String body = "{\"version\":0,\"title\":\"쿠키 인증으로 수정\"}";
    assertEquals(403, sendWithCookie("PUT", path, cookie, null, body).statusCode());
    assertEquals(403, sendWithCookie("PUT", path, cookie, "wrong-value", body).statusCode());
    assertEquals(
        401, sendWithCookie("PUT", path, "XSRF-TOKEN=csrf-value", "csrf-value", body).statusCode());
    var updated = sendWithCookie("PUT", path, cookie, "csrf-value", body);
    assertEquals(200, updated.statusCode());
    assertEquals("쿠키 인증으로 수정", data(updated).get("title").asText());
    path += "&version=" + data(updated).get("version").asLong();
    assertEquals(403, sendWithCookie("DELETE", path, cookie, null, null).statusCode());
    assertEquals(403, sendWithCookie("DELETE", path, cookie, "wrong-value", null).statusCode());
    assertEquals(
        401,
        sendWithCookie("DELETE", path, "XSRF-TOKEN=csrf-value", "csrf-value", null).statusCode());
    assertEquals(1, countDrafts());
    assertEquals(204, sendWithCookie("DELETE", path, cookie, "csrf-value", null).statusCode());
    assertEquals(0, countDrafts());
  }

  @Test
  void staleDeletePreservesTheUpdatedDraft() throws Exception {
    long id = data(save(101, "1", "{\"title\":\"처음 제목\"}")).get("draftId").asLong();
    updateDraft(101, id, "1", "{\"version\":0,\"title\":\"최신 제목\"}");
    assertEquals(409, deleteDraft(101, id, "1", 0).statusCode());
    assertEquals("최신 제목", data(readDraft(101, "1")).get("title").asText());
    assertEquals(204, deleteDraft(101, id, "1", 1).statusCode());
  }

  @Test
  void anOldTabCannotUpdateOrDeleteADraftRecreatedAtVersionZero() throws Exception {
    long oldId = data(save(101, "1", "{\"title\":\"옛 초안\"}")).get("draftId").asLong();
    assertEquals(204, deleteDraft(101, oldId, "1", 0).statusCode());
    long newId = data(save(101, "1", "{\"title\":\"새 초안\"}")).get("draftId").asLong();
    assertNotEquals(oldId, newId);
    assertEquals(
        409, updateDraft(101, oldId, "1", "{\"version\":0,\"title\":\"옛 창에서 덮어쓰기\"}").statusCode());
    assertEquals(409, deleteDraft(101, oldId, "1", 0).statusCode());
    assertEquals("새 초안", data(readDraft(101, "1")).get("title").asText());
  }

  @Test
  void serializesConcurrentUpdateAndDeleteWithoutLosingANewerDraft() throws Exception {
    long id = data(save(101, "1", "{}")).get("draftId").asLong();
    var update =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return updateDraft(101, id, "1", "{\"version\":0,\"title\":\"동시 수정\"}")
                    .statusCode();
              } catch (Exception exception) {
                throw new RuntimeException(exception);
              }
            });
    var deletion =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return deleteDraft(101, id, "1", 0).statusCode();
              } catch (Exception exception) {
                throw new RuntimeException(exception);
              }
            });
    var codes = List.of(update.join(), deletion.join());
    assertTrue(
        codes.equals(List.of(200, 409)) || codes.equals(List.of(404, 204)), codes.toString());
  }

  @Test
  void transferBlocksBothPreviousAndNewOwnerFromThePreviousOwnersDraft() throws Exception {
    long id = data(save(101, "1", "{\"title\":\"이전 소유자 비공개\"}")).get("draftId").asLong();
    jdbc.update("UPDATE products SET owner_id = 2 WHERE id = 101");
    for (String user : List.of("1", "2")) {
      assertEquals(404, readDraft(101, user).statusCode());
      assertEquals(404, updateDraft(101, id, user, "{\"version\":0}").statusCode());
      assertEquals(404, deleteDraft(101, id, user, 0).statusCode());
      assertEquals(0, data(send("GET", "/api/v1/listing-drafts", user, null)).get("items").size());
    }
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
    var suppliedVersion = JSON.readTree(body).get("version");
    if (suppliedVersion != null && !suppliedVersion.isNull()) {
      return updateDraft(productId, SAVED_DRAFT_IDS.getOrDefault(productId, 1L), user, body);
    }
    var response = send("POST", "/api/v1/products/" + productId + "/listing-draft", user, body);
    if (response.statusCode() == 201)
      SAVED_DRAFT_IDS.put(productId, data(response).get("draftId").asLong());
    return response;
  }

  private static HttpResponse<String> readDraft(long productId, String user) throws Exception {
    return send("GET", "/api/v1/products/" + productId + "/listing-draft", user, null);
  }

  private static HttpResponse<String> updateDraft(
      long productId, long draftId, String user, String body) throws Exception {
    return send(
        "PUT", "/api/v1/products/" + productId + "/listing-draft?draftId=" + draftId, user, body);
  }

  private static HttpResponse<String> deleteDraft(
      long productId, long draftId, String user, long version) throws Exception {
    return send(
        "DELETE",
        "/api/v1/products/"
            + productId
            + "/listing-draft?draftId="
            + draftId
            + "&version="
            + version,
        user,
        null);
  }

  private static HttpResponse<String> uploadPhoto(long productId) throws Exception {
    String boundary = "R01ProductPhotoBoundary";
    var body = new java.io.ByteArrayOutputStream();
    body.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"laptop.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    try (var image =
        new ClassPathResource("static/images/hero-laptop-passport.png").getInputStream()) {
      body.write(image.readAllBytes());
    }
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return HTTP.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/products/" + productId + "/images"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + token("1"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
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

  /** 쿠키와 CSRF 헤더를 직접 지정해 POST 요청을 보낸다. */
  private static HttpResponse<String> sendWithCookie(String path, String cookie, String csrfHeader)
      throws Exception {
    return sendWithCookie("POST", path, cookie, csrfHeader, "{}");
  }

  private static HttpResponse<String> sendWithCookie(
      String method, String path, String cookie, String csrfHeader, String body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie)
            .header("Content-Type", "application/json");
    if (csrfHeader != null) request.header("X-XSRF-TOKEN", csrfHeader);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
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
