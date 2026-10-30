package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.example.springtestci.ai.service.ListingSummaryRefreshListener;
import org.example.springtestci.ai.service.ListingSummaryStore;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.dto.CorrectHistoryEventRequest;
import org.example.springtestci.history.dto.CreateHistoryEventRequest;
import org.example.springtestci.history.dto.VoidHistoryEventRequest;
import org.example.springtestci.history.entity.HistoryEventType;
import org.example.springtestci.history.repository.HistoryRepository;
import org.example.springtestci.history.service.HistoryChangeReviewRecorder;
import org.example.springtestci.history.service.HistoryService;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingTradeCanceller;
import org.example.springtestci.product.event.ProductFactsChanged;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.service.InternalTransferCancellationPort;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.PurchaseRequestService;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.ReservedHistoryChangeReviewRecorder;
import org.example.springtestci.trade.service.TradeCancellationService;
import org.example.springtestci.trade.service.TradeListingCanceller;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 승인된 PostgreSQL에서만 실행하는 P3 취소·변경 확인 원자성 시험이다.
 *
 * <p>정식 history 스키마와 서비스를 사용한다. 내부 이전 취소 구현과 ownership 테이블은 시험 fixture이며 정식 ownership 연동 완료의 근거가
 * 아니다. 정식 ownership 테이블과 구분되는 trade_fixture_transfers를 사용하며 정식 스키마 제약은 별도 시험한다.
 */
@EnabledIfEnvironmentVariable(named = "P3_TRADE_TEST_ENABLED", matches = "true")
class TradeP3PostgresIntegrationTest {

  private static final long SELLER_ID = 1L;
  private static final long BUYER_ID = 2L;
  private static final long OTHER_BUYER_ID = 3L;
  private static final long PRODUCT_ID = 11L;
  private static final long LISTING_ID = 21L;
  private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");
  private static final String SCHEMA = "p3_trade_" + UUID.randomUUID().toString().replace("-", "");

  private static DataSource adminDataSource;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transactions;
  private static JdbcPurchaseRequestRepository requests;
  private static JdbcPublishedListingAccess listings;
  private static JdbcReservationChangeReviewRepository reviews;
  private static TestTransferCancellation transfers;
  private static JdbcRoomRepository rooms;
  private static JdbcTradeAuditRecorder audit;
  private static TradeCancellationService cancellations;
  private static ReservationChangeService changes;
  private static TradeListingCanceller listingCanceller;
  private static PurchaseRequestService purchaseRequests;

  @BeforeAll
  static void setUpDatabase() {
    String url = requiredEnvironment("P3_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P3_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P3_TRADE_TEST_DB_PASSWORD");
    adminDataSource = new DriverManagerDataSource(url, user, password);
    new JdbcTemplate(adminDataSource).execute("create schema " + SCHEMA);
    DataSource schemaDataSource =
        new DriverManagerDataSource(withCurrentSchema(url, SCHEMA), user, password);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc = new JdbcTemplate(schemaDataSource);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration", "classpath:db/pending/trade-p2")
        .outOfOrder(true)
        .load()
        .migrate();
    createOwnershipFixtureTable();
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations(
            "classpath:db/migration",
            "classpath:db/pending/trade-p2",
            "classpath:db/pending/trade-p3")
        .outOfOrder(true)
        .load()
        .migrate();

    transactions = new TransactionTemplate(new DataSourceTransactionManager(schemaDataSource));
    requests = new JdbcPurchaseRequestRepository(jdbc);
    listings = new JdbcPublishedListingAccess(jdbc);
    rooms = new JdbcRoomRepository(jdbc);
    transfers = new TestTransferCancellation(jdbc);
    var json = JsonMapper.builder().build();
    audit = new JdbcTradeAuditRecorder(jdbc, json);
    reviews = new JdbcReservationChangeReviewRepository(jdbc, json);
    cancellations =
        new TradeCancellationService(
            requests, rooms, listings, transfers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    changes =
        new ReservationChangeService(
            requests, reviews, rooms, listings, cancellations, Clock.fixed(NOW, ZoneOffset.UTC));
    listingCanceller = new TradeListingCanceller(requests, rooms, listings, cancellations);
    purchaseRequests =
        new PurchaseRequestService(requests, rooms, listings, new FixturePendingWriter(jdbc));
  }

  @AfterAll
  static void tearDownDatabase() {
    if (adminDataSource != null) {
      new JdbcTemplate(adminDataSource).execute("drop schema " + SCHEMA + " cascade");
    }
  }

  @BeforeEach
  void resetFixtures() {
    jdbc.execute(
        "truncate table transaction_change_reviews, trade_fixture_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, product_events, products, users, audit_logs restart identity cascade");
    insertUser(SELLER_ID, "seller-p3@example.test", "seller-p3");
    insertUser(BUYER_ID, "buyer-p3@example.test", "buyer-p3");
    insertUser(OTHER_BUYER_ID, "buyer2-p3@example.test", "buyer2-p3");
    transfers.failAfterUpdate.set(false);
  }

  @Test
  void commitsReservationCancellationAcrossTradeListingTransferAndAudit() {
    Fixture fixture = insertReservedFixture();

    var response =
        transactions.execute(
            ignored ->
                cancellations.cancel(
                    fixture.requestId(), BUYER_ID, TradeCancellationReason.CONDITION_MISMATCH));

    assertThat(response.listingStatus()).isEqualTo(PublishedListingStatus.ACTIVE);
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("ACTIVE");
    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
  }

  @Test
  void rollsBackEveryCancellationWriteWhenOwnershipCancellationFails() {
    Fixture fixture = insertReservedFixture();
    transfers.failAfterUpdate.set(true);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        cancellations.cancel(
                            fixture.requestId(),
                            SELLER_ID,
                            TradeCancellationReason.MUTUAL_AGREEMENT)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("ACCEPTED");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("PENDING");
  }

  @Test
  void rollsBackCancellationWhenListingReleaseFails() {
    Fixture fixture = insertReservedFixture();
    jdbc.execute(
        "create function fail_p3_listing_release() returns trigger language plpgsql as $$ begin if old.status = 'RESERVED' and new.status = 'ACTIVE' then raise exception 'injected listing release failure'; end if; return new; end $$");
    jdbc.execute(
        "create trigger fail_p3_listing_release before update on listings for each row execute function fail_p3_listing_release()");

    try {
      assertThatThrownBy(
              () ->
                  transactions.executeWithoutResult(
                      ignored ->
                          cancellations.cancel(
                              fixture.requestId(),
                              BUYER_ID,
                              TradeCancellationReason.CONDITION_MISMATCH)))
          .hasMessageContaining("injected listing release failure");
    } finally {
      jdbc.execute("drop trigger fail_p3_listing_release on listings");
      jdbc.execute("drop function fail_p3_listing_release()");
    }

    assertCancellationRolledBack(fixture.requestId());
  }

  @Test
  void rollsBackCancellationWhenAuditInsertFails() {
    Fixture fixture = insertReservedFixture();
    jdbc.execute(
        "create function fail_p3_cancellation_audit() returns trigger language plpgsql as $$ begin if new.action = 'TRADE_RESERVATION_CANCELLED' then raise exception 'injected cancellation audit failure'; end if; return new; end $$");
    jdbc.execute(
        "create trigger fail_p3_cancellation_audit before insert on audit_logs for each row execute function fail_p3_cancellation_audit()");

    try {
      assertThatThrownBy(
              () ->
                  transactions.executeWithoutResult(
                      ignored ->
                          cancellations.cancel(
                              fixture.requestId(),
                              SELLER_ID,
                              TradeCancellationReason.MUTUAL_AGREEMENT)))
          .hasMessageContaining("injected cancellation audit failure");
    } finally {
      jdbc.execute("drop trigger fail_p3_cancellation_audit on audit_logs");
      jdbc.execute("drop function fail_p3_cancellation_audit()");
    }

    assertCancellationRolledBack(fixture.requestId());
  }

  @Test
  void cancellationAndOtherPendingAcceptanceKeepOneValidReservation() throws Exception {
    Fixture fixture = insertReservedFixture();
    long otherRoom = insertRoom(OTHER_BUYER_ID);
    long pendingRequest =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING') returning id",
            Long.class,
            otherRoom,
            LISTING_ID);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    boolean accepted;
    try {
      var cancellation =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(
                    ignored ->
                        cancellations.cancel(
                            fixture.requestId(),
                            BUYER_ID,
                            TradeCancellationReason.SELF_CIRCUMSTANCES));
              });
      var acceptance =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return acceptOrConflict(pendingRequest);
              });
      start.countDown();
      cancellation.get(20, TimeUnit.SECONDS);
      accepted = acceptance.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select status from ownership_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    if (accepted) {
      assertThat(value("select status from listings where id = ?", LISTING_ID))
          .isEqualTo("RESERVED");
      assertThat(value("select request_status from purchase_requests where id = ?", pendingRequest))
          .isEqualTo("ACCEPTED");
      assertThat(
              value(
                  "select status from ownership_transfers where purchase_request_id = ?",
                  pendingRequest))
          .isEqualTo("PENDING");
    } else {
      assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("ACTIVE");
      assertThat(value("select request_status from purchase_requests where id = ?", pendingRequest))
          .isEqualTo("PENDING");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from ownership_transfers where purchase_request_id = ?",
                  Long.class,
                  pendingRequest))
          .isZero();
    }
  }

  @Test
  void simultaneousCancellationKeepsFirstMetadataAndSingleAudit() throws Exception {
    Fixture fixture = insertReservedFixture();
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var seller =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(
                    ignored ->
                        cancellations.cancel(
                            fixture.requestId(),
                            SELLER_ID,
                            TradeCancellationReason.MUTUAL_AGREEMENT));
              });
      var buyer =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(
                    ignored ->
                        cancellations.cancel(
                            fixture.requestId(),
                            BUYER_ID,
                            TradeCancellationReason.SELF_CIRCUMSTANCES));
              });
      start.countDown();
      assertThat(seller.get(20, TimeUnit.SECONDS).cancelledAt()).isEqualTo(NOW);
      assertThat(buyer.get(20, TimeUnit.SECONDS).cancelledAt()).isEqualTo(NOW);
    } finally {
      executor.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
  }

  @Test
  void storesJsonSnapshotOnceAndKeepsOtherReviewsUnreviewed() {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);

    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"changed\"}")));
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                102L,
                3L,
                JsonMapper.builder().build().readTree("{\"grade\":2}")));
    transactions.execute(
        ignored ->
            changes.review(fixture.requestId(), 101L, BUYER_ID, ChangeReviewDecision.KEEP, null));

    assertThat(reviews.countUnreviewed(fixture.requestId())).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select change_facts ->> 'title' from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                String.class,
                fixture.requestId()))
        .isEqualTo("changed");
  }

  /** H40-01~04: 실제 history 등록이 버전·P3 사본·확인 무효화·커밋 후 R10 접수를 함께 연결한다. */
  @Test
  void historyCreateConnectsFactsReviewConfirmationInvalidationAndAfterCommitRefresh() {
    Fixture fixture = insertReservedFixture();
    ListingSummaryStore summaries = mock(ListingSummaryStore.class);
    when(summaries.publicListingsForProduct(PRODUCT_ID)).thenReturn(List.of(LISTING_ID));
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getBeanFactory().registerSingleton("listingSummaryStore", summaries);
      context.registerBean(TransactionalEventListenerFactory.class);
      context.registerBean(ListingSummaryRefreshListener.class);
      context.refresh();
      HistoryService history =
          historyService(
              new ReservedHistoryChangeReviewRecorder(listings, requests, changes), context);

      transactions.execute(
          ignored -> history.create(SELLER_ID, PRODUCT_ID, generalHistoryRequest()));

      long eventId =
          jdbc.queryForObject(
              "select id from product_events where product_id = ? and event_type = 'REPAIR'",
              Long.class,
              PRODUCT_ID);
      assertThat(
              jdbc.queryForObject(
                  "select facts_version from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select facts_version from transaction_change_reviews where purchase_request_id = ? and event_id = ?",
                  Long.class,
                  fixture.requestId(),
                  eventId))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select change_facts ->> 'eventType' from transaction_change_reviews where purchase_request_id = ? and event_id = ?",
                  String.class,
                  fixture.requestId(),
                  eventId))
          .isEqualTo("REPAIR");
      assertThat(
              jdbc.queryForObject(
                  "select jsonb_exists(change_facts, 'title') from transaction_change_reviews where purchase_request_id = ? and event_id = ?",
                  Boolean.class,
                  fixture.requestId(),
                  eventId))
          .isFalse();
      assertThat(
              jdbc.queryForObject(
                  "select seller_confirmed_version from purchase_requests where id = ?",
                  Long.class,
                  fixture.requestId()))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "select buyer_confirmed_version from purchase_requests where id = ?",
                  Long.class,
                  fixture.requestId()))
          .isNull();
      verify(summaries).publicListingsForProduct(PRODUCT_ID);
      verify(summaries).refresh(LISTING_ID);
      var publicHistory =
          transactions.execute(
              ignored -> {
                new HistoryRepository(jdbc).lockOwnedProduct(PRODUCT_ID, SELLER_ID);
                return new org.example.springtestci.listing.repository.JdbcListingHistoryReader(
                        jdbc)
                    .read(PRODUCT_ID);
              });
      assertThat(publicHistory.sample()).isFalse();
      assertThat(publicHistory.state())
          .isEqualTo(org.example.springtestci.listing.service.ListingHistoryReader.State.READY);
      assertThat(publicHistory.events()).hasSize(1);
      assertThat(publicHistory.events().get(0).eventId()).isEqualTo(eventId);
      assertThat(publicHistory.events().get(0).text().reviewed()).isFalse();
      assertThat(publicHistory.events().get(0).text().title()).isNull();
      assertThat(publicHistory.events().get(0).text().content()).isNull();
    }
  }

  /** H40-02/05: 원본 사양은 보존하되 신규·기존 자유 문자열은 저장 사본과 구매자 응답에서 제외한다. */
  @Test
  void historySpecificationSnapshotExcludesFreeTextFromStoredReviewAndBuyerResponse() {
    Fixture fixture = insertReservedFixture();
    jdbc.update(
        "insert into laptop_details (product_id, cpu_spec, gpu_spec, ram_gb, storage_gb, storage_type, display_spec, battery_health_percent, os_name) values (?, 'PRIVATE_CPU_BEFORE', 'PRIVATE_GPU', 8, 256, 'SSD', 'PRIVATE_DISPLAY', 90, 'PRIVATE_OS')",
        PRODUCT_ID);
    HistoryService history =
        historyService(
            new ReservedHistoryChangeReviewRecorder(listings, requests, changes),
            mock(ApplicationEventPublisher.class));

    long eventId =
        transactions
            .execute(
                ignored ->
                    history.create(
                        SELLER_ID,
                        PRODUCT_ID,
                        new CreateHistoryEventRequest(
                            HistoryEventType.SPEC_CHANGE,
                            LocalDate.of(2026, 10, 25),
                            "사양 변경",
                            null,
                            null,
                            null,
                            null,
                            java.util.Map.of("cpu", "PRIVATE_CPU_AFTER", "ramGb", 16),
                            List.of())))
            .eventId();

    String storedFacts =
        value(
            "select change_facts::text from transaction_change_reviews where purchase_request_id = ?",
            fixture.requestId());
    assertThat(storedFacts)
        .doesNotContain("PRIVATE_CPU_BEFORE", "PRIVATE_CPU_AFTER", "PRIVATE_GPU")
        .contains("\"ramGb\": 8", "\"ramGb\": 16", "\"storageType\": \"SSD\"");
    assertThat(value("select specs_after ->> 'cpu' from product_events where id = ?", eventId))
        .isEqualTo("PRIVATE_CPU_AFTER");

    var buyerReview = changes.findAll(fixture.requestId(), BUYER_ID, 0, 20).items().get(0);
    assertThat(buyerReview.changeFacts().toString())
        .doesNotContain(
            "PRIVATE_CPU_BEFORE",
            "PRIVATE_CPU_AFTER",
            "PRIVATE_GPU",
            "PRIVATE_DISPLAY",
            "PRIVATE_OS");
    assertThat(buyerReview.changeFacts().get("specsBefore").get("ramGb").asInt()).isEqualTo(8);
    assertThat(buyerReview.changeFacts().get("specsAfter").get("ramGb").asInt()).isEqualTo(16);
  }

  /** H40-03/04: P3 저장 뒤 실패하면 history·버전·확인 변경이 모두 원복되고 R10 이벤트도 전달되지 않는다. */
  @Test
  void historyCreateRollsBackEveryWriteAndRefreshWhenReviewConnectionFails() {
    Fixture fixture = insertReservedFixture();
    HistoryChangeReviewRecorder delegate =
        new ReservedHistoryChangeReviewRecorder(listings, requests, changes);
    HistoryChangeReviewRecorder failing =
        (productId, eventId, factsVersion, snapshot) -> {
          delegate.recordCreated(productId, eventId, factsVersion, snapshot);
          throw new IllegalStateException("Injected history review failure");
        };
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    HistoryService history = historyService(failing, events);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> history.create(SELLER_ID, PRODUCT_ID, generalHistoryRequest())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("history review failure");

    assertThat(count("select count(*) from product_events where product_id = ?", PRODUCT_ID))
        .isZero();
    assertThat(
            count(
                "select count(*) from transaction_change_reviews where purchase_request_id = ?",
                fixture.requestId()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select facts_version from products where id = ?", Long.class, PRODUCT_ID))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select seller_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select buyer_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
    verify(events, never()).publishEvent(new ProductFactsChanged(PRODUCT_ID));
  }

  /** H40-01/04/07: P3 OFF에서도 실제 history 쓰기마다 버전과 R10 원천 이벤트가 정확히 한 번 반영된다. */
  @Test
  void historyLifecycleIncrementsOneVersionPerWriteAndPublishesRefreshWithP3Off() {
    insertStandaloneLaptop();
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    HistoryService history = historyService(null, events);

    long specEventId =
        transactions
            .execute(
                ignored ->
                    history.create(
                        SELLER_ID,
                        PRODUCT_ID,
                        new CreateHistoryEventRequest(
                            HistoryEventType.SPEC_CHANGE,
                            LocalDate.of(2026, 10, 24),
                            "메모리 증설",
                            null,
                            null,
                            null,
                            null,
                            java.util.Map.of("ramGb", 16),
                            List.of())))
            .eventId();
    long generalEventId =
        transactions
            .execute(ignored -> history.create(SELLER_ID, PRODUCT_ID, generalHistoryRequest()))
            .eventId();
    transactions.execute(
        ignored ->
            history.correct(
                SELLER_ID,
                PRODUCT_ID,
                generalEventId,
                new CorrectHistoryEventRequest(
                    LocalDate.of(2026, 10, 25),
                    "키보드 수리 정정",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "작업 명칭 정정")));
    transactions.execute(
        ignored ->
            history.voidEvent(
                SELLER_ID, PRODUCT_ID, generalEventId, new VoidHistoryEventRequest("중복 이력 무효화")));

    assertThat(specEventId).isPositive();
    assertThat(
            jdbc.queryForObject(
                "select ram_gb from laptop_details where product_id = ?",
                Integer.class,
                PRODUCT_ID))
        .isEqualTo(16);
    assertThat(
            jdbc.queryForObject(
                "select facts_version from products where id = ?", Long.class, PRODUCT_ID))
        .isEqualTo(5L);
    assertThat(value("select status from product_events where id = ?", generalEventId))
        .isEqualTo("VOID");
    assertThat(count("select count(*) from transaction_change_reviews")).isZero();
    verify(events, times(4)).publishEvent(new ProductFactsChanged(PRODUCT_ID));
  }

  @Test
  void newChangeAndKeepDecisionSerializeWithoutLosingEitherWrite() throws Exception {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"first\"}")));

    runConcurrently(
        () ->
            transactions.execute(
                ignored ->
                    changes.recordChange(
                        PRODUCT_ID,
                        fixture.requestId(),
                        102L,
                        3L,
                        JsonMapper.builder().build().readTree("{\"title\":\"second\"}"))),
        () ->
            transactions.execute(
                ignored ->
                    changes.review(
                        fixture.requestId(), 101L, BUYER_ID, ChangeReviewDecision.KEEP, null)));

    assertThat(
            value(
                "select decision from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("KEEP");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102 and decision is null",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(reviews.countUnreviewed(fixture.requestId())).isEqualTo(1L);
  }

  @Test
  void newChangeAndCancelDecisionLeaveNoPartialCancellation() throws Exception {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"first\"}")));
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    boolean newChangeCommitted;
    try {
      var newChange =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return recordChangeOrConflict(fixture.requestId(), 102L);
              });
      var cancellation =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return transactions.execute(
                    ignored ->
                        changes.review(
                            fixture.requestId(),
                            101L,
                            BUYER_ID,
                            ChangeReviewDecision.CANCEL,
                            TradeCancellationReason.CONDITION_MISMATCH));
              });
      start.countDown();
      newChangeCommitted = newChange.get(20, TimeUnit.SECONDS);
      cancellation.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("ACTIVE");
    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select decision from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("CANCEL");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102",
                Long.class,
                fixture.requestId()))
        .isEqualTo(newChangeCommitted ? 1L : 0L);
  }

  @Test
  void listingShutdownNeverReactivatesCancelledListingAndCancelsAllRequests() {
    Fixture fixture = insertReservedFixture();
    long otherRoom = insertRoom(OTHER_BUYER_ID);
    long pendingRequest =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING') returning id",
            Long.class,
            otherRoom,
            LISTING_ID);
    jdbc.update(
        "update listings set status = 'CANCELLED', active_slot = null where id = ?", LISTING_ID);

    transactions.executeWithoutResult(
        ignored ->
            listingCanceller.cancel(
                new ListingTradeCanceller.Command(
                    PRODUCT_ID,
                    LISTING_ID,
                    SELLER_ID,
                    ListingStatus.RESERVED,
                    "MUTUAL_AGREEMENT")));

    assertThat(value("select status from listings where id = ?", LISTING_ID))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(value("select request_status from purchase_requests where id = ?", pendingRequest))
        .isEqualTo("CANCELLED");
  }

  @Test
  void listingShutdownAndCancellationAlwaysLeaveListingCancelled() throws Exception {
    Fixture fixture = insertReservedFixture();

    runConcurrently(
        () ->
            transactions.executeWithoutResult(
                ignored -> {
                  var listing =
                      listings
                          .findByIdForUpdate(LISTING_ID)
                          .orElseThrow(() -> new AssertionError("listing fixture missing"));
                  int updated =
                      jdbc.update(
                          "update listings set status = 'CANCELLED', active_slot = null where id = ? and status = ?",
                          LISTING_ID,
                          listing.status().name());
                  assertThat(updated).isEqualTo(1);
                  listingCanceller.cancel(
                      new ListingTradeCanceller.Command(
                          PRODUCT_ID,
                          LISTING_ID,
                          SELLER_ID,
                          ListingStatus.valueOf(listing.status().name()),
                          "MUTUAL_AGREEMENT"));
                }),
        () ->
            transactions.execute(
                ignored ->
                    cancellations.cancel(
                        fixture.requestId(),
                        BUYER_ID,
                        TradeCancellationReason.SELF_CIRCUMSTANCES)));

    assertThat(value("select status from listings where id = ?", LISTING_ID))
        .isEqualTo("CANCELLED");
    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select status from ownership_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
  }

  @Test
  void cancellationMetadataConstraintRejectsNullGapsAndKeepsValidCancellationStates() {
    Fixture fixture = insertReservedFixture();

    jdbc.update(
        "insert into purchase_requests (room_id, listing_id, request_status, transaction_status, cancelled_by_id, cancel_reason, cancelled_at) values (?, ?, 'CANCELLED', 'CANCELLED', ?, 'MUTUAL_AGREEMENT', ?)",
        fixture.roomId(),
        LISTING_ID,
        SELLER_ID,
        Timestamp.from(NOW));
    jdbc.update(
        "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'CANCELLED')",
        fixture.roomId(),
        LISTING_ID);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into purchase_requests (room_id, listing_id, request_status, transaction_status, cancelled_by_id, cancelled_at) values (?, ?, 'CANCELLED', 'CANCELLED', ?, ?)",
                    fixture.roomId(),
                    LISTING_ID,
                    SELLER_ID,
                    Timestamp.from(NOW)))
        .hasMessageContaining("ck_purchase_requests_cancellation_metadata");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into purchase_requests (room_id, listing_id, request_status, cancelled_by_id, cancel_reason, cancelled_at) values (?, ?, 'CANCELLED', ?, 'MUTUAL_AGREEMENT', ?)",
                    fixture.roomId(),
                    LISTING_ID,
                    SELLER_ID,
                    Timestamp.from(NOW)))
        .hasMessageContaining("ck_purchase_requests_cancellation_metadata");
  }

  /** P3-C01: ACTIVE 복구 쓰기가 반영된 직후 실패해도 이전 취소와 요청 메타데이터까지 원복한다. */
  @Test
  void p3C01RollsBackCancellationWhenListingReleaseFailsAfterUpdate() {
    Fixture fixture = insertReservedFixture();
    var failingListings = spy(listings);
    doAnswer(
            invocation -> {
              listings.releaseReservation(invocation.getArgument(0));
              throw new IllegalStateException("Injected listing release failure");
            })
        .when(failingListings)
        .releaseReservation(anyLong());
    var service =
        new TradeCancellationService(
            requests, rooms, failingListings, transfers, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        service.cancel(
                            fixture.requestId(),
                            BUYER_ID,
                            TradeCancellationReason.CONDITION_MISMATCH)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("listing release");

    assertReservedFixtureUnchanged(fixture);
  }

  /** P3-C02: 감사 INSERT 뒤 실패가 전파되면 취소 관련 모든 쓰기와 감사 행을 함께 원복한다. */
  @Test
  void p3C02RollsBackCancellationWhenAuditInsertFails() {
    Fixture fixture = insertReservedFixture();
    var failingAudit = spy(audit);
    doAnswer(
            invocation -> {
              audit.reservationCancelled(
                  invocation.getArgument(0),
                  invocation.getArgument(1),
                  invocation.getArgument(2),
                  invocation.getArgument(3),
                  invocation.getArgument(4));
              throw new IllegalStateException("Injected audit insert failure");
            })
        .when(failingAudit)
        .reservationCancelled(
            anyLong(),
            anyLong(),
            anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    var service =
        new TradeCancellationService(
            requests, rooms, listings, transfers, failingAudit, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        service.cancel(
                            fixture.requestId(),
                            SELLER_ID,
                            TradeCancellationReason.MUTUAL_AGREEMENT)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audit insert");

    assertReservedFixtureUnchanged(fixture);
  }

  /** P3-C03: 기존 예약 취소와 다른 PENDING 수락을 직렬화하고 과거 취소 재시도가 새 예약을 해제하지 않게 한다. */
  @Test
  void p3C03CancellationAndOtherAcceptanceKeepOneCurrentReservation() throws Exception {
    Fixture fixture = insertReservedFixture();
    long otherRoomId = insertRoom(OTHER_BUYER_ID);
    long otherRequestId =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING') returning id",
            Long.class,
            otherRoomId,
            LISTING_ID);

    runConcurrentlyAllowingTradeConflict(
        () ->
            transactions.execute(
                ignored ->
                    cancellations.cancel(
                        fixture.requestId(), BUYER_ID, TradeCancellationReason.SELF_CIRCUMSTANCES)),
        () -> transactions.execute(ignored -> purchaseRequests.accept(otherRequestId, SELLER_ID)));

    if (value("select request_status from purchase_requests where id = ?", otherRequestId)
        .equals("PENDING")) {
      transactions.execute(ignored -> purchaseRequests.accept(otherRequestId, SELLER_ID));
    }
    transactions.execute(
        ignored ->
            cancellations.cancel(
                fixture.requestId(), BUYER_ID, TradeCancellationReason.SELF_CIRCUMSTANCES));

    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(value("select request_status from purchase_requests where id = ?", otherRequestId))
        .isEqualTo("ACCEPTED");
    assertThat(
            value("select transaction_status from purchase_requests where id = ?", otherRequestId))
        .isEqualTo("RESERVED");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                otherRequestId))
        .isEqualTo("PENDING");
    assertThat(
            count(
                "select count(*) from trade_fixture_transfers where product_id = ? and status = 'PENDING'",
                PRODUCT_ID))
        .isEqualTo(1L);
  }

  /** P3-C04: 미응답 review의 최초 KEEP와 새 사건 등록이 경합해도 결정·사본·예약 상태가 일관된다. */
  @Test
  void p3C04FirstKeepAndNewChangeRacePreserveSnapshotsAndReservation() throws Exception {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"first\"}")));
    jdbc.update(
        "update purchase_requests set seller_confirmed_version = 2, buyer_confirmed_version = 2 where id = ?",
        fixture.requestId());

    runConcurrently(
        () ->
            transactions.execute(
                ignored ->
                    changes.recordChange(
                        PRODUCT_ID,
                        fixture.requestId(),
                        102L,
                        3L,
                        JsonMapper.builder().build().readTree("{\"grade\":2}"))),
        () ->
            transactions.execute(
                ignored ->
                    changes.review(
                        fixture.requestId(), 101L, BUYER_ID, ChangeReviewDecision.KEEP, null)));

    assertThat(
            value(
                "select decision from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("KEEP");
    assertThat(
            jdbc.queryForObject(
                "select reviewed_at from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                Timestamp.class,
                fixture.requestId()))
        .isNotNull();
    assertThat(
            value(
                "select change_facts ->> 'title' from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("first");
    assertThat(
            count(
                "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102 and decision is null",
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            value(
                "select change_facts ->> 'grade' from transaction_change_reviews where purchase_request_id = ? and event_id = 102",
                fixture.requestId()))
        .isEqualTo("2");
    assertThat(
            jdbc.queryForObject(
                "select seller_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select buyer_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("RESERVED");
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(
            count(
                "select count(*) from trade_fixture_transfers where purchase_request_id = ? and status = 'PENDING'",
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            count(
                "select count(*) from product_events where event_type = 'TRANSFER' and product_id = ?",
                PRODUCT_ID))
        .isZero();
  }

  /** P3-C04: 이미 저장된 KEEP 재요청과 새 사건 등록이 경합해도 새 사본만 미확인으로 남는다. */
  @Test
  void p3C04NewChangeAndRepeatedKeepPreserveSnapshotsAndInvalidateConfirmations() throws Exception {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"first\"}")));
    transactions.execute(
        ignored ->
            changes.review(fixture.requestId(), 101L, BUYER_ID, ChangeReviewDecision.KEEP, null));
    jdbc.update(
        "update purchase_requests set seller_confirmed_version = 2, buyer_confirmed_version = 2 where id = ?",
        fixture.requestId());

    runConcurrently(
        () ->
            transactions.execute(
                ignored ->
                    changes.recordChange(
                        PRODUCT_ID,
                        fixture.requestId(),
                        102L,
                        3L,
                        JsonMapper.builder().build().readTree("{\"grade\":2}"))),
        () ->
            transactions.execute(
                ignored ->
                    changes.review(
                        fixture.requestId(), 101L, BUYER_ID, ChangeReviewDecision.KEEP, null)));

    assertThat(
            value(
                "select decision from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("KEEP");
    assertThat(
            value(
                "select change_facts ->> 'title' from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("first");
    assertThat(
            count(
                "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102 and decision is null",
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select seller_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select buyer_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
  }

  /** P3-C04: 새 사건 등록과 CANCEL 경합은 취소 또는 사본 선행만 허용하고 최종 거래를 일관되게 종료한다. */
  @Test
  void p3C04NewChangeAndCancelRaceLeavesConsistentCancellation() throws Exception {
    Fixture fixture = insertReservedFixture();
    insertProductEvent(101L);
    insertProductEvent(102L);
    transactions.execute(
        ignored ->
            changes.recordChange(
                PRODUCT_ID,
                fixture.requestId(),
                101L,
                2L,
                JsonMapper.builder().build().readTree("{\"title\":\"first\"}")));

    runConcurrentlyAllowingTradeConflict(
        () ->
            transactions.execute(
                ignored ->
                    changes.recordChange(
                        PRODUCT_ID,
                        fixture.requestId(),
                        102L,
                        3L,
                        JsonMapper.builder().build().readTree("{\"grade\":2}"))),
        () ->
            transactions.execute(
                ignored ->
                    changes.review(
                        fixture.requestId(),
                        101L,
                        BUYER_ID,
                        ChangeReviewDecision.CANCEL,
                        TradeCancellationReason.CONDITION_MISMATCH)));

    assertThat(
            value(
                "select decision from transaction_change_reviews where purchase_request_id = ? and event_id = 101",
                fixture.requestId()))
        .isEqualTo("CANCEL");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("ACTIVE");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            count(
                "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102",
                fixture.requestId()))
        .isBetween(0L, 1L);
    if (count(
            "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102",
            fixture.requestId())
        == 1L) {
      assertThat(
              value(
                  "select change_facts ->> 'grade' from transaction_change_reviews where purchase_request_id = ? and event_id = 102",
                  fixture.requestId()))
          .isEqualTo("2");
      assertThat(
              count(
                  "select count(*) from transaction_change_reviews where purchase_request_id = ? and event_id = 102 and decision is null",
                  fixture.requestId()))
          .isEqualTo(1L);
    }
    assertThat(
            count(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);
  }

  /** P3-C05: 판매 중지와 일반 취소의 어느 직렬 순서에서도 확정된 CANCELLED 판매글을 ACTIVE로 복구하지 않는다. */
  @Test
  void p3C05ListingShutdownAndCancellationNeverReactivateStoppedListing() throws Exception {
    Fixture fixture = insertReservedFixture();
    long otherRoomId = insertRoom(OTHER_BUYER_ID);
    long pendingRequestId =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING') returning id",
            Long.class,
            otherRoomId,
            LISTING_ID);

    runConcurrentlyAllowingTradeConflict(
        () ->
            transactions.execute(
                ignored ->
                    cancellations.cancel(
                        fixture.requestId(), SELLER_ID, TradeCancellationReason.MUTUAL_AGREEMENT)),
        () ->
            transactions.executeWithoutResult(
                ignored -> {
                  var locked = listings.findByIdForUpdate(LISTING_ID).orElseThrow();
                  int stopped =
                      jdbc.update(
                          "update listings set status = 'CANCELLED', active_slot = null where id = ? and status = ?",
                          LISTING_ID,
                          locked.status().name());
                  if (stopped != 1) throw new IllegalStateException("Expected listing shutdown");
                  listingCanceller.cancel(
                      new ListingTradeCanceller.Command(
                          PRODUCT_ID,
                          LISTING_ID,
                          SELLER_ID,
                          ListingStatus.valueOf(locked.status().name()),
                          TradeCancellationReason.MUTUAL_AGREEMENT.name()));
                }));

    assertThat(value("select status from listings where id = ?", LISTING_ID))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(value("select request_status from purchase_requests where id = ?", pendingRequestId))
        .isEqualTo("CANCELLED");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("CANCELLED");
    assertThat(
            count(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);
  }

  private static void createOwnershipFixtureTable() {
    jdbc.execute(
        "create table trade_fixture_transfers (id bigint generated by default as identity primary key, product_id bigint not null references products(id), purchase_request_id bigint not null unique references purchase_requests(id), from_user_id bigint not null references users(id), to_user_id bigint not null references users(id), transfer_type varchar(20) not null, status varchar(20) not null)");
    jdbc.execute(
        "create unique index uq_p3_fixture_pending_transfer on trade_fixture_transfers(product_id) where status = 'PENDING'");
  }

  private Fixture insertReservedFixture() {
    jdbc.update(
        "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (?, 'LAPTOP', 'maker', 'MAKER', 'model', 'p3-fingerprint', 'title', ?, 'ciphertext')",
        PRODUCT_ID,
        SELLER_ID);
    jdbc.update(
        "insert into listings (id, product_id, seller_id, title, price, condition_grade, description, status, published_at, source_draft_id, source_draft_version, active_slot) values (?, ?, ?, 'title', 1, 1, 'description', 'RESERVED', current_timestamp, 1, 0, 1)",
        LISTING_ID,
        PRODUCT_ID,
        SELLER_ID);
    long roomId = insertRoom(BUYER_ID);
    long requestId =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status, transaction_status, seller_confirmed_version, buyer_confirmed_version) values (?, ?, 'ACCEPTED', 'RESERVED', 1, 1) returning id",
            Long.class,
            roomId,
            LISTING_ID);
    jdbc.update(
        "insert into trade_fixture_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, 'INTERNAL', 'PENDING')",
        PRODUCT_ID,
        requestId,
        SELLER_ID,
        BUYER_ID);
    return new Fixture(requestId, roomId);
  }

  private void insertStandaloneLaptop() {
    jdbc.update(
        "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (?, 'LAPTOP', 'maker', 'MAKER', 'model', 'h40-fingerprint', 'title', ?, 'ciphertext')",
        PRODUCT_ID,
        SELLER_ID);
    jdbc.update(
        "insert into laptop_details (product_id, ram_gb, storage_gb, storage_type) values (?, 8, 256, 'SSD')",
        PRODUCT_ID);
  }

  @SuppressWarnings("unchecked")
  private HistoryService historyService(
      HistoryChangeReviewRecorder recorder, ApplicationEventPublisher events) {
    ObjectProvider<HistoryChangeReviewRecorder> provider = mock(ObjectProvider.class);
    if (recorder != null) when(provider.getIfAvailable()).thenReturn(recorder);
    return new HistoryService(
        new HistoryRepository(jdbc),
        JsonMapper.builder().build(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        provider,
        events);
  }

  private CreateHistoryEventRequest generalHistoryRequest() {
    return new CreateHistoryEventRequest(
        HistoryEventType.REPAIR,
        LocalDate.of(2026, 10, 25),
        "홍길동 키보드 수리",
        "010-1234-5678로 연락",
        "개인 수리점",
        120000L,
        "키보드",
        null,
        List.of());
  }

  private long insertRoom(long buyerId) {
    return jdbc.queryForObject(
        "insert into chat_rooms (listing_id, seller_id, inquirer_id) values (?, ?, ?) returning id",
        Long.class,
        LISTING_ID,
        SELLER_ID,
        buyerId);
  }

  private void insertProductEvent(long eventId) {
    jdbc.update(
        "insert into product_events (id, product_id, author_id, event_type, occurred_on, title, specs_before, specs_after, status) values (?, ?, ?, 'SPEC_CHANGE', current_date, 'spec change', cast(? as jsonb), cast(? as jsonb), 'VALID')",
        eventId,
        PRODUCT_ID,
        SELLER_ID,
        "{\"ram\":\"16GB\"}",
        "{\"ram\":\"32GB\"}");
  }

  private void assertCancellationRolledBack(long requestId) {
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(value("select request_status from purchase_requests where id = ?", requestId))
        .isEqualTo("ACCEPTED");
    assertThat(value("select transaction_status from purchase_requests where id = ?", requestId))
        .isEqualTo("RESERVED");
    assertThat(
            value(
                "select status from ownership_transfers where purchase_request_id = ?", requestId))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                Long.class,
                requestId))
        .isZero();
  }

  private boolean acceptOrConflict(long requestId) {
    try {
      transactions.execute(ignored -> purchaseRequests.accept(requestId, SELLER_ID));
      return true;
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT) {
        throw exception;
      }
      return false;
    }
  }

  private boolean recordChangeOrConflict(long requestId, long eventId) {
    try {
      transactions.execute(
          ignored ->
              changes.recordChange(
                  PRODUCT_ID,
                  requestId,
                  eventId,
                  3L,
                  JsonMapper.builder().build().readTree("{\"title\":\"concurrent\"}")));
      return true;
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT) {
        throw exception;
      }
      return false;
    }
  }

  private void runConcurrently(Runnable first, Runnable second) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var firstResult =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                first.run();
                return null;
              });
      var secondResult =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                second.run();
                return null;
              });
      start.countDown();
      firstResult.get(20, TimeUnit.SECONDS);
      secondResult.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  private void insertUser(long id, String email, String nickname) {
    jdbc.update(
        "insert into users (id, email, password_hash, nickname, role, created_at, updated_at) values (?, ?, 'hash', ?, 'USER', ?, ?)",
        id,
        email,
        nickname,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private String value(String sql, Object parameter) {
    return jdbc.queryForObject(sql, String.class, parameter);
  }

  private long count(String sql, Object... parameters) {
    return jdbc.queryForObject(sql, Long.class, parameters);
  }

  private void assertReservedFixtureUnchanged(Fixture fixture) {
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("ACCEPTED");
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("RESERVED");
    assertThat(
            jdbc.queryForObject(
                "select cancelled_by_id from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select cancel_reason from purchase_requests where id = ?",
                String.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select cancelled_at from purchase_requests where id = ?",
                Timestamp.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select seller_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select buyer_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("PENDING");
    assertThat(count("select count(*) from audit_logs where target_id = ?", fixture.requestId()))
        .isZero();
  }

  private void runConcurrentlyAllowingTradeConflict(Runnable first, Runnable second)
      throws Exception {
    runConcurrently(
        () -> runIgnoringExpectedConflict(first), () -> runIgnoringExpectedConflict(second));
  }

  private void runConcurrently(Runnable first, Runnable second) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var firstResult =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                first.run();
                return null;
              });
      var secondResult =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                second.run();
                return null;
              });
      start.countDown();
      firstResult.get(20, TimeUnit.SECONDS);
      secondResult.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  private void runIgnoringExpectedConflict(Runnable operation) {
    try {
      operation.run();
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT) throw exception;
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private static String withCurrentSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  private record Fixture(long requestId, long roomId) {}

  /** rollback과 정확한 조건부 갱신을 관찰하기 위한 시험 전용 ownership 취소 대역이다. */
  private static class TestTransferCancellation implements InternalTransferCancellationPort {

    private final JdbcTemplate jdbc;
    private final AtomicBoolean failAfterUpdate = new AtomicBoolean();

    private TestTransferCancellation(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public void cancelPending(Command command) {
      int updated =
          jdbc.update(
              "update trade_fixture_transfers set status = 'CANCELLED' where product_id = ? and purchase_request_id = ? and from_user_id = ? and to_user_id = ? and transfer_type = 'INTERNAL' and status = 'PENDING'",
              command.productId(),
              command.purchaseRequestId(),
              command.fromUserId(),
              command.toUserId());
      if (updated != 1) {
        throw new IllegalStateException("Expected one pending internal transfer");
      }
      if (failAfterUpdate.get()) {
        throw new IllegalStateException("Injected ownership cancellation failure");
      }
    }
  }

  /** 다른 구매자의 합법적인 재예약을 실제 같은 트랜잭션에 저장하는 시험 전용 ownership writer다. */
  private static class FixturePendingWriter implements InternalTransferPendingWriter {

    private final JdbcTemplate jdbc;

    private FixturePendingWriter(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public void createPending(Command command) {
      int inserted =
          jdbc.update(
              "insert into trade_fixture_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, ?, ?)",
              command.productId(),
              command.purchaseRequestId(),
              command.fromUserId(),
              command.toUserId(),
              command.transferType(),
              command.status());
      if (inserted != 1) throw new IllegalStateException("Expected one pending transfer");
    }
  }

  private static class JdbcRoomRepository implements ChatRoomRepository {

    private final JdbcTemplate jdbc;

    private JdbcRoomRepository(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public Optional<ChatRoom> findById(long roomId) {
      return jdbc
          .query(
              "select id, listing_id, seller_id, inquirer_id, created_at from chat_rooms where id = ?",
              (row, number) ->
                  new ChatRoom(
                      row.getLong("id"),
                      row.getLong("listing_id"),
                      row.getLong("seller_id"),
                      row.getLong("inquirer_id"),
                      row.getTimestamp("created_at").toInstant()),
              roomId)
          .stream()
          .findFirst();
    }

    @Override
    public Optional<ChatRoom> findByListingIdAndInquirerId(long listingId, long inquirerId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Page<ChatRoom> findByParticipantId(long participantId, Pageable pageable) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChatRoom create(long listingId, long sellerId, long inquirerId) {
      throw new UnsupportedOperationException();
    }
  }
}
