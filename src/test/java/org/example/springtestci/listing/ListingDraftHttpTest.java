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

  @Test
  void requiresAuthenticationAndTheCurrentOwner() throws Exception {
    assertEquals(401, save(101, null, "{}").statusCode());
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
