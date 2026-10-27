package org.example.springtestci.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.example.springtestci.ai.repository.ListingSummaryRepository;
import org.example.springtestci.ai.service.*;
import org.example.springtestci.common.error.*;
import org.example.springtestci.listing.service.*;
import org.example.springtestci.listing.service.ListingHistoryReader.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 HTTP/JWT/CSRF·Flyway·DB에서 비동기 상태와 공개 경계를 검증한다. 외부 AI·이력만 대역이다. */
class ListingSummaryHttpTest {
  private static final String SECRET = "r09-http-test-only-jwt-secret-at-least-32-bytes";
  private static final String PATH = "/api/v1/listings/1/summary";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final AtomicReference<Snapshot> HISTORY = new AtomicReference<>();
  private static final AtomicReference<String> INPUT = new AtomicReference<>();
  private static final AtomicReference<Runnable> DURING = new AtomicReference<>();
  private static final AtomicReference<ErrorCode> ERROR = new AtomicReference<>();
  private static final AtomicBoolean CONFIGURED = new AtomicBoolean(true),
      IN_TRANSACTION = new AtomicBoolean(),
      PUBLISH_FAIL = new AtomicBoolean();
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final AiSuggestionRateLimiter LIMITS = mock(AiSuggestionRateLimiter.class);
  private static ConfigurableApplicationContext context;
  private static JdbcTemplate jdbc;
  private static ListingSummaryStore store;
  private static ListingSummaryRepository repository;
  private static ListingSummaryProcessor processor;
  private static String base, schema;
  private static Path files;

  @TestConfiguration(proxyBeanMethods = false)
  public static class Fixtures {
    @Bean
    ListingHistoryReader summaryHistory() {
      return productId -> HISTORY.get();
    }

    @Bean
    ListingPublishConstraints summaryConstraints() {
      return productId -> new ListingPublishConstraints.Snapshot(true, false, false);
    }

    @Bean
    ListingHistoryPublisher summaryHistoryPublisher() {
      return (p, l, u) -> {
        if (PUBLISH_FAIL.get()) throw new CommonException(ErrorCode.LISTING_PUBLISH_UNAVAILABLE);
      };
    }

    @Bean
    ListingTradeCanceller summaryTradeCanceller() {
      return command -> {};
    }

    @Bean
    ListingHistoryPrivatizer summaryHistoryPrivatizer() {
      return (p, l, u) -> {};
    }

    @Bean
    @Primary
    AiSuggestionRateLimiter summaryLimits() {
      return LIMITS;
    }

    @Bean
    @Primary
    ListingSummaryGateway summaryGateway() {
      return new ListingSummaryGateway() {
        public boolean configured() {
          return CONFIGURED.get();
        }

        public List<String> summarize(String input) {
          CALLS.incrementAndGet();
          INPUT.set(input);
          IN_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
          if (DURING.get() != null) DURING.get().run();
          if (ERROR.get() != null) throw new CommonException(ERROR.get());
          return List.of(
              "등록된 사양은 Apple M3 Pro입니다.",
              "등록된 메모리는 18GB입니다.",
              "전체 누적 핵심 이력은 아래 타임라인에서 확인할 수 있습니다.");
        }
      };
    }
  }

  @BeforeAll
  static void start() throws Exception {
    String url = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = url != null && !url.isBlank();
    if (postgres && !url.contains("sale_ai_test"))
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    schema =
        postgres ? "listing_summary_r09_" + Long.toUnsignedString(System.nanoTime()) : "public";
    if (postgres) url += (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    else
      url =
          "jdbc:h2:mem:listing_summary_r09;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    files = Files.createTempDirectory("listing-summary-r09-http-");
    for (String name : List.of("front", "side", "back", "thinkpad", "other"))
      try (var input =
          new ClassPathResource("static/images/hero-laptop-passport.png").getInputStream()) {
        Files.copy(input, files.resolve("draft-demo-" + name + ".png"));
      }
    var app = new SpringApplication(SpringTestCiApplication.class);
    app.addPrimarySources(List.of(Fixtures.class));
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
            "--app.jwt.secret=" + SECRET,
            "--app.storage.local-root=" + files,
            "--app.ai.listing.api-key=",
            "--app.ai.summary.enabled=true",
            "--app.ai.summary.initial-delay-ms=600000");
    jdbc = context.getBean(JdbcTemplate.class);
    store = context.getBean(ListingSummaryStore.class);
    repository = context.getBean(ListingSummaryRepository.class);
    processor = context.getBean(ListingSummaryProcessor.class);
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES (1,'r09-owner@fixture.invalid','unused','r09-owner','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'r09-other@fixture.invalid','unused','r09-other','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
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
          || !files.getFileName().toString().startsWith("listing-summary-r09-http-"))
        throw new IllegalStateException("Unexpected cleanup path");
      try (var paths = Files.walk(files)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  @BeforeEach
  void reset() {
    DURING.set(null);
    ERROR.set(null);
    INPUT.set(null);
    CALLS.set(0);
    CONFIGURED.set(true);
    IN_TRANSACTION.set(false);
    PUBLISH_FAIL.set(false);
    HISTORY.set(new Snapshot(State.READY, false, List.of()));
    org.mockito.Mockito.reset(LIMITS);
    when(LIMITS.acquire(anyLong()))
        .thenReturn(new AiSuggestionRateLimiter.Lease("fixture", "fixture"));
    jdbc.update("DELETE FROM listing_images");
    jdbc.update("DELETE FROM listings");
    jdbc.update("DELETE FROM draft_images");
    jdbc.update("DELETE FROM listing_drafts");
    jdbc.update("DELETE FROM audit_logs");
    jdbc.update("UPDATE products SET owner_id=1,facts_version=7,archived_at=NULL WHERE id=101");
    jdbc.update(
        "INSERT INTO listings(id,product_id,seller_id,title,price,condition_grade,description,status,content_version,published_at,source_draft_id,source_draft_version,active_slot) VALUES (1,101,1,'판매글 제목',1200000,4,'개인 메모 person@fixture.invalid 010-1234-5678','ACTIVE',2,CURRENT_TIMESTAMP,9001,0,1)");
    jdbc.update(
        "INSERT INTO listing_images(listing_id,product_image_id,sort_order,is_primary) VALUES(1,301,0,TRUE)");
  }

  @Test
  void ownerQueuesThenWorkerStoresSummaryAndMemberReadsIt() throws Exception {
    assertEquals("NOT_REQUESTED", data(get(PATH, "2")).get("status").asString());
    var accepted = post(PATH, "1", "");
    assertEquals(202, accepted.statusCode(), accepted.body());
    assertEquals("QUEUED", JSON.readTree(accepted.body()).at("/data/status").asString());
    assertEquals(0, CALLS.get());
    var pending = repository.pending().get(0);
    processor.process(pending);
    var result = get(PATH, "2");
    var value = data(result);
    assertEquals("READY", value.get("status").asString());
    assertTrue(value.get("text").asString().contains("18GB"));
    assertFalse(value.get("canRequest").asBoolean());
    assertEquals(
        7,
        jdbc.queryForObject(
            "SELECT summary_source_product_version FROM listings WHERE id=1", Long.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT summary_source_listing_version FROM listings WHERE id=1", Long.class));
    assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    assertFalse(result.body().contains("attemptId"));
    assertFalse(result.body().contains("sourceFingerprint"));
    assertFalse(INPUT.get().contains("person@"));
    assertFalse(INPUT.get().contains("010-1234"));
    assertFalse(IN_TRANSACTION.get());
    assertEquals(1, CALLS.get());
    verify(LIMITS).release(any());
    assertEquals("READY", data(get("/api/v1/listings/1", "2")).at("/summary/status").asString());
  }

  @Test
  void creationAndReadAreProtected() throws Exception {
    assertEquals(401, get(PATH, null).statusCode());
    assertEquals(401, post(PATH, null, "").statusCode());
    assertEquals(404, post(PATH, "2", "").statusCode());
    assertEquals(0, CALLS.get());
    assertEquals(404, get(PATH.replace("/1/", "/9999/"), "1").statusCode());
  }

  @Test
  void reservedIsReadableButEndedArchivedOrTransferredIsNot() throws Exception {
    jdbc.update("UPDATE listings SET status='RESERVED' WHERE id=1");
    assertEquals(200, get(PATH, "2").statusCode());
    assertEquals(202, post(PATH, "1", "").statusCode());
    processor.process(repository.pending().get(0));
    assertEquals("READY", data(get(PATH, "2")).get("status").asString());
    for (String status : List.of("SOLD", "CANCELLED")) {
      jdbc.update("UPDATE listings SET status=?,active_slot=NULL WHERE id=1", status);
      assertEquals(404, get(PATH, "1").statusCode());
      assertEquals(404, post(PATH, "1", "").statusCode());
    }
    jdbc.update("UPDATE listings SET status='ACTIVE',active_slot=1 WHERE id=1");
    jdbc.update("UPDATE products SET archived_at=CURRENT_TIMESTAMP WHERE id=101");
    assertEquals(404, get(PATH, "1").statusCode());
    jdbc.update("UPDATE products SET archived_at=NULL,owner_id=2 WHERE id=101");
    assertEquals(404, get(PATH, "2").statusCode());
  }

  @Test
  void repeatedQueueAndReadyRequestsDoNotCreateNewAttempts() throws Exception {
    post(PATH, "1", "");
    var first = repository.pending().get(0);
    post(PATH, "1", "");
    assertEquals(first, repository.pending().get(0));
    processor.process(first);
    post(PATH, "1", "");
    assertEquals(
        first.attemptId(),
        jdbc.queryForObject("SELECT summary_attempt_id FROM listings WHERE id=1", UUID.class));
    assertEquals(1, CALLS.get());
    assertTrue(repository.pending().isEmpty());
  }

  @Test
  void aiFailureKeepsBasicDetailAndAllowsDelayedRetry() throws Exception {
    post(PATH, "1", "");
    ERROR.set(ErrorCode.AI_TIMEOUT);
    processor.process(repository.pending().get(0));
    var value = data(get(PATH, "1"));
    assertEquals("FAILED", value.get("status").asString());
    assertTrue(value.get("text").isNull());
    assertEquals("AI_002", value.get("errorCode").asString());
    assertFalse(value.get("canRequest").asBoolean());
    assertEquals(200, get("/api/v1/listings/1", "2").statusCode());
    var limited = post(PATH, "1", "");
    assertEquals(429, limited.statusCode());
    assertTrue(limited.headers().firstValue("Retry-After").isPresent());
    age(61);
    ERROR.set(null);
    assertTrue(data(get(PATH, "1")).get("canRequest").asBoolean());
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertEquals("READY", data(get(PATH, "2")).get("status").asString());
  }

  @Test
  void missingKeyAndUnavailableHistoryFailWithoutExternalCall() throws Exception {
    CONFIGURED.set(false);
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertEquals("AI_001", data(get(PATH, "1")).get("errorCode").asString());
    assertEquals(0, CALLS.get());
    age(61);
    CONFIGURED.set(true);
    HISTORY.set(Snapshot.unavailable());
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertEquals("FAILED", repository.read(1).status().name());
    assertEquals("AI_004", repository.read(1).errorCode());
    assertEquals(0, CALLS.get());
  }

  @Test
  void redisLimitFailsSummaryWhileBasicDetailRemains() throws Exception {
    when(LIMITS.acquire(1))
        .thenThrow(new RateLimitedException(ErrorCode.AI_RATE_LIMITED, Duration.ofSeconds(30)));
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertEquals("AI_007", data(get(PATH, "1")).get("errorCode").asString());
    assertEquals(0, CALLS.get());
    assertEquals(200, get("/api/v1/listings/1", "2").statusCode());
  }

  @Test
  void changedFactsOrListingBeforeProcessingAvoidsExternalCall() throws Exception {
    post(PATH, "1", "");
    jdbc.update("UPDATE products SET facts_version=facts_version+1 WHERE id=101");
    processor.process(repository.pending().get(0));
    assertEquals("AI_005", data(get(PATH, "1")).get("errorCode").asString());
    assertEquals(0, CALLS.get());
    post(PATH, "1", "");
    jdbc.update("UPDATE listings SET content_version=content_version+1 WHERE id=1");
    processor.process(repository.pending().get(0));
    assertEquals(0, CALLS.get());
    assertEquals("AI_005", data(get(PATH, "1")).get("errorCode").asString());
  }

  @Test
  void changedFactsDuringGenerationDiscardResult() throws Exception {
    post(PATH, "1", "");
    DURING.set(() -> jdbc.update("UPDATE products SET facts_version=facts_version+1 WHERE id=101"));
    processor.process(repository.pending().get(0));
    assertEquals("AI_005", repository.read(1).errorCode());
    assertNull(repository.read(1).text());
  }

  @Test
  void changedHistoryEvenWithoutVersionBumpDiscardsResult() throws Exception {
    post(PATH, "1", "");
    DURING.set(
        () ->
            HISTORY.set(
                new Snapshot(
                    State.READY,
                    false,
                    List.of(event(701, EventStatus.VALID, true, "새로운 점검 기록")))));
    processor.process(repository.pending().get(0));
    assertEquals("AI_005", repository.read(1).errorCode());
    assertNull(repository.read(1).text());
  }

  @Test
  void closedDuringGenerationDoesNotStoreLateResultOrHoldDatabaseLocks() throws Exception {
    post(PATH, "1", "");
    DURING.set(
        () -> {
          try {
            CompletableFuture.runAsync(
                    () -> {
                      try {
                        assertEquals(
                            200,
                            post(
                                    "/api/v1/listings/1/cancel",
                                    "1",
                                    "{\"version\":2,\"reason\":\"R09 테스트 판매 중지\"}")
                                .statusCode());
                      } catch (Exception e) {
                        throw new IllegalStateException(e);
                      }
                    })
                .get(3, TimeUnit.SECONDS);
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    processor.process(repository.pending().get(0));
    assertEquals(
        "CANCELLED", jdbc.queryForObject("SELECT status FROM listings WHERE id=1", String.class));
    assertNull(repository.read(1).text());
    assertEquals("FAILED", repository.read(1).status().name());
    assertEquals(404, get(PATH, "2").statusCode());
  }

  @Test
  void soldAndOwnershipTransferWhileModelRunsDiscardResult() throws Exception {
    post(PATH, "1", "");
    DURING.set(() -> jdbc.update("UPDATE listings SET status='SOLD',active_slot=NULL WHERE id=1"));
    processor.process(repository.pending().get(0));
    assertNull(repository.read(1).text());
    assertEquals(404, get(PATH, "2").statusCode());
    jdbc.update("UPDATE listings SET status='ACTIVE',active_slot=1 WHERE id=1");
    age(61);
    DURING.set(() -> jdbc.update("UPDATE products SET owner_id=2 WHERE id=101"));
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertNull(repository.read(1).text());
    assertEquals(404, get(PATH, "2").statusCode());
  }

  @Test
  void oldAttemptCannotOverwriteNewSuccessfulAttempt() throws Exception {
    post(PATH, "1", "");
    var old = repository.pending().get(0);
    var job = store.claim(old);
    store.fail(old, "AI_002");
    age(61);
    post(PATH, "1", "");
    var next = repository.pending().get(0);
    assertNotEquals(old.attemptId(), next.attemptId());
    processor.process(next);
    var text = repository.read(1).text();
    store.complete(job, "오래된 결과");
    store.fail(old, "AI_003");
    assertEquals(text, repository.read(1).text());
    assertEquals(next.attemptId(), repository.read(1).attemptId());
    assertEquals("READY", repository.read(1).status().name());
  }

  @Test
  void concurrentWorkersClaimTheSameAttemptOnlyOnce() throws Exception {
    post(PATH, "1", "");
    var pending = repository.pending().get(0);
    var first = CompletableFuture.runAsync(() -> processor.process(pending));
    var second = CompletableFuture.runAsync(() -> processor.process(pending));
    CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);
    assertEquals(1, CALLS.get());
    assertEquals("READY", repository.read(1).status().name());
  }

  @Test
  void dispatcherReturnsWhileProviderIsStillRunning() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    DURING.set(
        () -> {
          entered.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    try {
      assertEquals(202, post(PATH, "1", "").statusCode());
      context.getBean(ListingSummaryDispatcher.class).dispatch();
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      assertEquals("PROCESSING", data(get(PATH, "2")).get("status").asString());
      assertEquals(200, get("/api/v1/listings/1", "2").statusCode());
    } finally {
      release.countDown();
    }
    for (int i = 0; i < 100 && repository.read(1).status().name().equals("PROCESSING"); i++)
      Thread.sleep(20);
    assertEquals("READY", repository.read(1).status().name());
  }

  @Test
  void abandonedQueuedAndProcessingAttemptsRecoverToFailure() throws Exception {
    post(PATH, "1", "");
    var job = repository.pending().get(0);
    store.claim(job);
    age(300);
    store.recoverExpired();
    assertEquals("FAILED", repository.read(1).status().name());
    assertEquals("AI_002", repository.read(1).errorCode());
    age(61);
    post(PATH, "1", "");
    age(301);
    store.recoverExpired();
    assertEquals("FAILED", repository.read(1).status().name());
  }

  @Test
  void staleReadySummaryIsNotReturnedAndJpaEditsDoNotEraseItsMetadata() throws Exception {
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    var text = repository.read(1).text();
    var update =
        send(
            "PUT",
            "/api/v1/listings/1",
            "1",
            "{\"version\":2,\"title\":\"수정된 제목\",\"price\":1300000,\"description\":\"수정 설명\",\"conditionGrade\":4,\"selectedImageIds\":[301]}");
    assertEquals(200, update.statusCode(), update.body());
    assertEquals(text, repository.read(1).text());
    var result = data(get(PATH, "2"));
    assertEquals("FAILED", result.get("status").asString());
    assertTrue(result.get("text").isNull());
    assertEquals("AI_005", result.get("errorCode").asString());
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    assertEquals("READY", data(get(PATH, "2")).get("status").asString());
  }

  @Test
  void automaticPublishQueuesOnlyAfterCommitAndKeepsDuplicatePublishIdempotent() throws Exception {
    emptyListings();
    var draft =
        data(
            post(
                "/api/v1/products/101/listing-draft",
                "1",
                "{\"title\":\"새 게시글\",\"description\":\"제품 설명\",\"price\":1200000,\"conditionGrade\":4,\"selectedImageIds\":[301]}"),
            201);
    String body =
        "{\"draftId\":"
            + draft.get("draftId").asLong()
            + ",\"version\":"
            + draft.get("version").asLong()
            + "}";
    var published = post("/api/v1/products/101/listings", "1", body);
    long id = data(published, 201).get("listingId").asLong();
    assertEquals("QUEUED", repository.read(id).status().name());
    var attempt = repository.read(id).attemptId();
    assertEquals(0, CALLS.get());
    assertEquals(200, post("/api/v1/products/101/listings", "1", body).statusCode());
    assertEquals(attempt, repository.read(id).attemptId());
  }

  @Test
  void publishRollbackDoesNotQueueAnyAiWork() throws Exception {
    emptyListings();
    var draft =
        data(
            post(
                "/api/v1/products/101/listing-draft",
                "1",
                "{\"title\":\"새 게시글\",\"description\":\"제품 설명\",\"price\":1200000,\"conditionGrade\":4,\"selectedImageIds\":[301]}"),
            201);
    PUBLISH_FAIL.set(true);
    var result =
        post(
            "/api/v1/products/101/listings",
            "1",
            "{\"draftId\":" + draft.get("draftId").asLong() + ",\"version\":0}");
    assertEquals(503, result.statusCode());
    assertTrue(repository.pending().isEmpty());
    assertEquals(0, CALLS.get());
  }

  @Test
  void allHistoryIsIncludedButVoidAndUnreviewedTextIsExcluded() throws Exception {
    var events = new ArrayList<Event>();
    for (int i = 0; i < 121; i++) events.add(event(5000 + i, EventStatus.VALID, true, "공개 점검 기록"));
    events.add(event(6001, EventStatus.VOID, true, "invalid-old-fact"));
    events.add(event(6002, EventStatus.VALID, false, "private-person@fixture.invalid"));
    HISTORY.set(new Snapshot(State.READY, false, events));
    post(PATH, "1", "");
    processor.process(repository.pending().get(0));
    var input = JSON.readTree(INPUT.get());
    assertEquals(123, input.at("/productAndHistory/history").size());
    assertFalse(INPUT.get().contains("invalid-old-fact"));
    assertFalse(INPUT.get().contains("private-person"));
    assertTrue(INPUT.get().contains("previousOwner\":true"));
  }

  @Test
  void cookiesNeedMatchingCsrfForRequestButNotForRead() throws Exception {
    String cookie = "access_token=" + token("1") + "; XSRF-TOKEN=r09-csrf";
    var request =
        HttpRequest.newBuilder(URI.create(base + PATH))
            .header("Cookie", cookie)
            .POST(HttpRequest.BodyPublishers.noBody());
    assertEquals(
        403, HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    assertEquals(
        202,
        HTTP.send(
                request.header("X-XSRF-TOKEN", "r09-csrf").build(),
                HttpResponse.BodyHandlers.ofString())
            .statusCode());
  }

  @Test
  void databaseRejectsPartialAndInconsistentSummaryState() {
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "UPDATE listings SET summary_status='READY',summary_text='text' WHERE id=1"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "UPDATE listings SET summary_status=NULL,summary_text='text',summary_source_product_version=1,summary_source_listing_version=1,summary_attempt_id=?,summary_updated_at=CURRENT_TIMESTAMP WHERE id=1",
                UUID.randomUUID()));
    assertEquals("NOT_REQUESTED", repository.read(1).status().name());
  }

  private static Event event(long id, EventStatus status, boolean reviewed, String text) {
    return new Event(
        id,
        101,
        2L,
        EventType.INSPECTION,
        LocalDate.parse("2025-05-20"),
        Instant.parse("2025-05-20T00:00:00Z"),
        status,
        new PublicText("제품 점검", text, null, null, reviewed),
        List.of());
  }

  private static void emptyListings() {
    jdbc.update("DELETE FROM listing_images");
    jdbc.update("DELETE FROM listings");
  }

  private static void age(long seconds) {
    jdbc.update(
        "UPDATE listings SET summary_updated_at=? WHERE id=1",
        java.sql.Timestamp.from(Instant.now().minusSeconds(seconds)));
  }

  private static JsonNode data(HttpResponse<String> response) {
    return data(response, 200);
  }

  private static JsonNode data(HttpResponse<String> response, int status) {
    assertEquals(status, response.statusCode(), response.body());
    return JSON.readTree(response.body()).get("data");
  }

  private static HttpResponse<String> get(String path, String user) throws Exception {
    return send("GET", path, user, null);
  }

  private static HttpResponse<String> post(String path, String user, String body) throws Exception {
    return send("POST", path, user, body);
  }

  private static HttpResponse<String> send(String method, String path, String user, String body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (user != null) request.header("Authorization", "Bearer " + token(user));
    else
      request.header("Cookie", "XSRF-TOKEN=r09-anonymous").header("X-XSRF-TOKEN", "r09-anonymous");
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String token(String user) throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String header =
        encoder.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    long now = Instant.now().getEpochSecond();
    String payload =
        encoder.encodeToString(
            ("{\"sub\":\"" + user + "\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}")
                .getBytes(StandardCharsets.UTF_8));
    String content = header + "." + payload;
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return content
        + "."
        + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
  }
}
