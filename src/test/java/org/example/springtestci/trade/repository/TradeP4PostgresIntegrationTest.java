package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.dto.CreateHistoryEventRequest;
import org.example.springtestci.history.entity.HistoryEventType;
import org.example.springtestci.history.repository.HistoryRepository;
import org.example.springtestci.history.service.HistoryChangeReviewRecorder;
import org.example.springtestci.history.service.HistoryService;
import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingDetailService;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.example.springtestci.listing.service.ListingTradeCanceller;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.service.ProductService;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.service.InternalTradeCompletionPort;
import org.example.springtestci.trade.service.InternalTransferCancellationPort;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.ReservedHistoryChangeReviewRecorder;
import org.example.springtestci.trade.service.TradeAuditRecorder;
import org.example.springtestci.trade.service.TradeCancellationService;
import org.example.springtestci.trade.service.TradeCompletionService;
import org.example.springtestci.trade.service.TradeListingCanceller;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 승인된 전용 PostgreSQL에서만 실행하는 P4 완료·취소·변경 경합 검증이다.
 *
 * <p>정식 history 스키마와 서비스를 사용한다. ownership, history 공개 상태, 완료 문서 포트는 원자성 시험용 fixture이며 정식 운영 구현이
 * 아니다. ACCEPTED 완료 상태는 trade_fixture_transfers 대역 계약만 의미하며 정식 COMPLETED 상태로 매핑하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "P4_TRADE_TEST_ENABLED", matches = "true")
class TradeP4PostgresIntegrationTest {

  private static final long SELLER_ID = 1L;
  private static final long BUYER_ID = 2L;
  private static final long OTHER_BUYER_ID = 3L;
  private static final long PRODUCT_ID = 11L;
  private static final long LISTING_ID = 21L;
  private static final long FACTS_VERSION = 1L;
  private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
  private static final String SCHEMA = "p4_trade_" + UUID.randomUUID().toString().replace("-", "");

  private static DataSource adminDataSource;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transactions;
  private static JdbcPurchaseRequestRepository requests;
  private static JdbcPublishedListingAccess listings;
  private static JdbcRoomRepository rooms;
  private static FixtureCompletionPort completionPort;
  private static FaultInjectingAuditRecorder audit;
  private static TradeCompletionService completions;
  private static TradeCancellationService cancellations;
  private static TradeListingCanceller listingCanceller;
  private static ProductService products;
  private static ListingDetailService details;
  private static ReservationChangeService changes;

  @BeforeAll
  static void setUpDatabase() {
    String url = requiredEnvironment("P4_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P4_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P4_TRADE_TEST_DB_PASSWORD");
    adminDataSource = new DriverManagerDataSource(url, user, password);
    new JdbcTemplate(adminDataSource).execute("create schema " + SCHEMA);
    DataSource schemaDataSource =
        new DriverManagerDataSource(withCurrentSchema(url, SCHEMA), user, password);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration", "classpath:db/pending/trade-p2")
        .outOfOrder(true)
        .load()
        .migrate();

    jdbc = new JdbcTemplate(schemaDataSource);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations(
            "classpath:db/migration",
            "classpath:db/pending/trade-p2",
            "classpath:db/pending/trade-p3")
        .outOfOrder(true)
        .load()
        .migrate();
    createCompletionFixtureTables();
    transactions = new TransactionTemplate(new DataSourceTransactionManager(schemaDataSource));
    requests = new JdbcPurchaseRequestRepository(jdbc);
    listings = new JdbcPublishedListingAccess(jdbc);
    products = mock(ProductService.class);
    details = mock(ListingDetailService.class);
    completionPort = new FixtureCompletionPort(jdbc);
    var json = JsonMapper.builder().build();
    audit = new FaultInjectingAuditRecorder(new JdbcTradeAuditRecorder(jdbc, json));
    rooms = new JdbcRoomRepository(jdbc);
    cancellations =
        new TradeCancellationService(
            requests,
            rooms,
            listings,
            new FixtureCancellationPort(jdbc),
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
    changes =
        new ReservationChangeService(
            requests,
            new JdbcReservationChangeReviewRepository(jdbc, json),
            rooms,
            listings,
            cancellations,
            Clock.fixed(NOW, ZoneOffset.UTC));
    completions =
        new TradeCompletionService(
            requests,
            rooms,
            listings,
            products,
            details,
            changes,
            completionPort,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
    listingCanceller = new TradeListingCanceller(requests, rooms, listings, cancellations);
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
        "truncate table transaction_change_reviews, completion_drafts, completion_documents, product_history_state, product_events, trade_fixture_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, products, users, audit_logs restart identity cascade");
    insertUser(SELLER_ID, "seller-p4@example.test", "seller-p4");
    insertUser(BUYER_ID, "buyer-p4@example.test", "buyer-p4");
    insertUser(OTHER_BUYER_ID, "buyer2-p4@example.test", "buyer2-p4");
    completionPort.failAfterWrites.set(false);
    audit.failAfterInsert.set(false);
    reset(products, details);
    when(products.getForListing(SELLER_ID, PRODUCT_ID)).thenAnswer(ignored -> currentProduct());
    when(details.get(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(officialDetail());
  }

  @Test
  void p4Db01CompletesTradeAndEveryRequiredFixtureSideEffect() {
    Fixture fixture = insertReservedFixture(null, null);

    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), SELLER_ID, FACTS_VERSION));
    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION));

    assertCompletedFixture(fixture);
  }

  @Test
  void p4Db02RollsBackLastConfirmationAndAllFinalWritesOnPortOrAuditFailure() {
    Fixture fixture = insertReservedFixture(null, null);
    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), SELLER_ID, FACTS_VERSION));
    completionPort.failAfterWrites.set(true);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)))
        .isInstanceOf(IllegalStateException.class);
    assertRolledBackLastConfirmation(fixture);

    resetFixtures();
    Fixture auditFixture = insertReservedFixture(null, null);
    transactions.execute(
        ignored -> completions.complete(auditFixture.requestId(), SELLER_ID, FACTS_VERSION));
    audit.failAfterInsert.set(true);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        completions.complete(auditFixture.requestId(), BUYER_ID, FACTS_VERSION)))
        .isInstanceOf(IllegalStateException.class);
    assertRolledBackLastConfirmation(auditFixture);
  }

  /** P4-DB02-B: 거래 COMPLETED UPDATE 직후 실패해도 선행 첫 확인만 남기고 마지막 확인과 완료 효과를 원복한다. */
  @Test
  void p4Db02bRollsBackWhenTradeCompletionUpdateFailsAfterWrite() {
    Fixture fixture = insertReservedFixture(null, null);
    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), SELLER_ID, FACTS_VERSION));
    var failingRequests = spy(requests);
    doAnswer(
            invocation -> {
              requests.complete(invocation.getArgument(0), invocation.getArgument(1));
              throw new IllegalStateException("Injected trade completion update failure");
            })
        .when(failingRequests)
        .complete(anyLong(), anyLong());
    TradeCompletionService failingCompletion = completionService(failingRequests, listings);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        failingCompletion.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("trade completion update");

    assertRolledBackLastConfirmation(fixture);
  }

  /** P4-DB02-B: SOLD UPDATE 직후 실패해도 거래 UPDATE와 마지막 확인을 포함한 완료 쓰기를 모두 원복한다. */
  @Test
  void p4Db02bRollsBackWhenSoldUpdateFailsAfterWrite() {
    Fixture fixture = insertReservedFixture(null, null);
    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), SELLER_ID, FACTS_VERSION));
    var failingListings = spy(listings);
    doAnswer(
            invocation -> {
              listings.sell(invocation.getArgument(0));
              throw new IllegalStateException("Injected sold update failure");
            })
        .when(failingListings)
        .sell(anyLong());
    TradeCompletionService failingCompletion = completionService(requests, failingListings);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored ->
                        failingCompletion.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sold update");

    assertRolledBackLastConfirmation(fixture);
  }

  @Test
  void p4Db03ConcurrentConfirmationAndRetryProduceOneCompletion() throws Exception {
    Fixture fixture = insertReservedFixture(null, null);
    runConcurrently(
        () ->
            transactions.execute(
                ignored -> completions.complete(fixture.requestId(), SELLER_ID, FACTS_VERSION)),
        () ->
            transactions.execute(
                ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)));

    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION));

    assertCompletedFixture(fixture);
    assertThat(
            count(
                "select count(*) from product_events pe join trade_fixture_transfers ot on ot.id = pe.transfer_request_id where ot.purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);
    assertThat(
            count(
                "select count(*) from audit_logs where action = 'TRADE_COMPLETED' and target_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);
  }

  @Test
  void p4Db04CancellationAndCompletionRaceLeaveExactlyOneTerminalOutcome() throws Exception {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, null);
    runConcurrentlyAllowingConflict(
        () ->
            transactions.execute(
                ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)),
        () ->
            transactions.execute(
                ignored ->
                    cancellations.cancel(
                        fixture.requestId(), SELLER_ID, TradeCancellationReason.MUTUAL_AGREEMENT)));

    String transactionStatus =
        value("select transaction_status from purchase_requests where id = ?", fixture.requestId());
    String listingStatus = value("select status from listings where id = ?", LISTING_ID);
    String transferStatus =
        value(
            "select status from trade_fixture_transfers where purchase_request_id = ?",
            fixture.requestId());
    assertThat(transactionStatus).isIn("COMPLETED", "CANCELLED");
    if (transactionStatus.equals("COMPLETED")) {
      assertThat(listingStatus).isEqualTo("SOLD");
      assertThat(transferStatus).isEqualTo("ACCEPTED");
      assertThat(
              jdbc.queryForObject(
                  "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(BUYER_ID);
      assertThat(
              value(
                  "select visibility from product_history_state where product_id = ?", PRODUCT_ID))
          .isEqualTo("PRIVATE");
      assertThat(
              count(
                  "select count(*) from audit_logs where action = 'TRADE_COMPLETED' and target_id = ?",
                  fixture.requestId()))
          .isEqualTo(1L);
      assertThat(
              count(
                  "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                  fixture.requestId()))
          .isZero();
    } else {
      assertThat(listingStatus).isEqualTo("ACTIVE");
      assertThat(transferStatus).isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(SELLER_ID);
      assertThat(
              value(
                  "select visibility from product_history_state where product_id = ?", PRODUCT_ID))
          .isEqualTo("PUBLIC");
      assertThat(
              count(
                  "select count(*) from product_events pe join trade_fixture_transfers ot on ot.id = pe.transfer_request_id where ot.purchase_request_id = ?",
                  fixture.requestId()))
          .isZero();
      assertThat(
              count(
                  "select count(*) from audit_logs where action = 'TRADE_COMPLETED' and target_id = ?",
                  fixture.requestId()))
          .isZero();
      assertThat(
              count(
                  "select count(*) from audit_logs where action = 'TRADE_RESERVATION_CANCELLED' and target_id = ?",
                  fixture.requestId()))
          .isEqualTo(1L);
    }
  }

  @Test
  void p4Db05ProductChangeAndLastConfirmationRespectProductLock() throws Exception {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, null);
    runConcurrentlyAllowingConflict(
        () ->
            transactions.execute(
                ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)),
        () ->
            transactions.executeWithoutResult(
                ignored -> {
                  listings.findByIdForUpdate(LISTING_ID).orElseThrow();
                  int changed =
                      jdbc.update(
                          "update products set facts_version = facts_version + 1 where id = ? and owner_id = ?",
                          PRODUCT_ID,
                          SELLER_ID);
                  if (changed == 1) {
                    long eventId =
                        jdbc.queryForObject(
                            "insert into product_events (product_id, author_id, event_type, occurred_on, title, specs_before, specs_after, status) values (?, ?, 'SPEC_CHANGE', current_date, 'spec change', cast(? as jsonb), cast(? as jsonb), 'VALID') returning id",
                            Long.class,
                            PRODUCT_ID,
                            SELLER_ID,
                            "{\"ram\":\"16GB\"}",
                            "{\"ram\":\"32GB\"}");
                    changes.recordChange(
                        PRODUCT_ID,
                        fixture.requestId(),
                        eventId,
                        FACTS_VERSION + 1,
                        JsonMapper.builder().build().createObjectNode().put("field", "ram"));
                  }
                }));

    String status =
        value("select transaction_status from purchase_requests where id = ?", fixture.requestId());
    if (status.equals("COMPLETED")) {
      assertThat(
              jdbc.queryForObject(
                  "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(BUYER_ID);
      assertThat(
              count(
                  "select count(*) from transaction_change_reviews where purchase_request_id = ?",
                  fixture.requestId()))
          .isZero();
    } else {
      assertThat(
              jdbc.queryForObject(
                  "select facts_version from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select buyer_confirmed_version from purchase_requests where id = ?",
                  Long.class,
                  fixture.requestId()))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "select seller_confirmed_version from purchase_requests where id = ?",
                  Long.class,
                  fixture.requestId()))
          .isNull();
      assertThat(
              count(
                  "select count(*) from transaction_change_reviews where purchase_request_id = ? and decision is null",
                  fixture.requestId()))
          .isEqualTo(1L);
    }
  }

  @Test
  void p4Db06ListingShutdownAndCompletionDoNotReviveOrCompleteAnotherRequest() throws Exception {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, null);
    runConcurrentlyAllowingConflict(
        () ->
            transactions.execute(
                ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)),
        () ->
            transactions.executeWithoutResult(
                ignored -> {
                  listings.findByIdForUpdate(LISTING_ID).orElseThrow();
                  int stopped =
                      jdbc.update(
                          "update listings set status = 'CANCELLED', active_slot = null where id = ? and status = 'RESERVED'",
                          LISTING_ID);
                  if (stopped == 1) {
                    listingCanceller.cancel(
                        new ListingTradeCanceller.Command(
                            PRODUCT_ID,
                            LISTING_ID,
                            SELLER_ID,
                            ListingStatus.RESERVED,
                            TradeCancellationReason.MUTUAL_AGREEMENT.name()));
                  }
                }));

    String listingStatus = value("select status from listings where id = ?", LISTING_ID);
    String targetStatus =
        value("select transaction_status from purchase_requests where id = ?", fixture.requestId());
    assertThat(List.of("SOLD:COMPLETED", "CANCELLED:CANCELLED"))
        .contains(listingStatus + ":" + targetStatus);
    String otherStatus =
        value(
            "select request_status from purchase_requests where id = ?", fixture.otherRequestId());
    if (targetStatus.equals("COMPLETED")) {
      assertThat(otherStatus).isEqualTo("REJECTED");
      assertThat(
              count(
                  "select count(*) from product_events pe join trade_fixture_transfers ot on ot.id = pe.transfer_request_id where ot.purchase_request_id = ?",
                  fixture.requestId()))
          .isEqualTo(1L);
    } else {
      assertThat(otherStatus).isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select cancelled_by_id from purchase_requests where id = ?",
                  Long.class,
                  fixture.requestId()))
          .isEqualTo(SELLER_ID);
      assertThat(
              value(
                  "select cancel_reason from purchase_requests where id = ?", fixture.requestId()))
          .isEqualTo(TradeCancellationReason.MUTUAL_AGREEMENT.name());
      assertThat(
              count(
                  "select count(*) from product_events pe join trade_fixture_transfers ot on ot.id = pe.transfer_request_id where ot.purchase_request_id = ?",
                  fixture.requestId()))
          .isZero();
    }
  }

  @Test
  void p4Db07ExistingConstraintsAndPendingCleanupPreserveEarlierStates() {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, FACTS_VERSION);

    transactions.execute(
        ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION));

    assertThat(
            value("select request_status from purchase_requests where id = ?", fixture.requestId()))
        .isEqualTo("ACCEPTED");
    assertThat(
            value(
                "select request_status from purchase_requests where id = ?",
                fixture.otherRequestId()))
        .isEqualTo("REJECTED");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into purchase_requests (room_id, listing_id, request_status, transaction_status) values (?, ?, 'PENDING', 'COMPLETED')",
                    fixture.otherRoomId(),
                    LISTING_ID))
        .isInstanceOf(RuntimeException.class);
  }

  /** H40-01~03: 실제 history 등록이 과거 양측 확인을 지우고 P4 재완료를 미확인 변경으로 차단한다. */
  @Test
  void historyCreateInvalidatesBothConfirmationsAndBlocksCompletionUntilReview() {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, FACTS_VERSION);
    HistoryService history = historyService(mock(ApplicationEventPublisher.class));

    transactions.execute(ignored -> history.create(SELLER_ID, PRODUCT_ID, generalHistoryRequest()));

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
            count(
                "select count(*) from transaction_change_reviews where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> completions.complete(fixture.requestId(), SELLER_ID, 2L)))
        .isInstanceOf(CommonException.class)
        .extracting(failure -> ((CommonException) failure).errorCode())
        .isEqualTo(ErrorCode.TRADE_CONFLICT);
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("RESERVED");
  }

  /** H40-03: 완료와 실제 history 등록 경합은 완료 또는 변경 대기 중 하나의 일관된 결과만 남긴다. */
  @Test
  void completionAndHistoryCreateRaceLeaveOneConsistentOutcome() throws Exception {
    Fixture fixture = insertReservedFixture(FACTS_VERSION, null);
    HistoryService history = historyService(mock(ApplicationEventPublisher.class));

    RaceOutcomes outcomes =
        runConcurrentlyAllowingHistoryConflict(
            () ->
                transactions.executeWithoutResult(
                    ignored -> history.create(SELLER_ID, PRODUCT_ID, generalHistoryRequest())),
            () ->
                transactions.executeWithoutResult(
                    ignored -> completions.complete(fixture.requestId(), BUYER_ID, FACTS_VERSION)));

    String status =
        value("select transaction_status from purchase_requests where id = ?", fixture.requestId());
    long historyCount =
        count(
            "select count(*) from product_events where product_id = ? and event_type = 'REPAIR'",
            PRODUCT_ID);
    if (status.equals("COMPLETED")) {
      assertThat(outcomes.historyFailure()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
      assertThat(outcomes.completionFailure()).isNull();
      assertThat(historyCount).isZero();
      assertThat(
              jdbc.queryForObject(
                  "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
          .isEqualTo(BUYER_ID);
    } else {
      assertThat(outcomes.historyFailure()).isNull();
      assertThat(outcomes.completionFailure()).isEqualTo(ErrorCode.TRADE_CONFLICT);
      assertThat(status).isEqualTo("RESERVED");
      assertThat(historyCount).isEqualTo(1L);
      assertThat(
              count(
                  "select count(*) from transaction_change_reviews where purchase_request_id = ?",
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
  }

  @Test
  void completionRacePolicyDoesNotHideResourceNotFound() {
    assertThatThrownBy(
            () ->
                runAllowingCompletionConflict(
                    () -> {
                      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
                    }))
        .isInstanceOf(CommonException.class)
        .extracting(failure -> ((CommonException) failure).errorCode())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  private static void createCompletionFixtureTables() {
    jdbc.execute(
        "create table trade_fixture_transfers (id bigint generated by default as identity primary key, product_id bigint not null references products(id), purchase_request_id bigint not null unique references purchase_requests(id), from_user_id bigint not null references users(id), to_user_id bigint not null references users(id), transfer_type varchar(20) not null, status varchar(20) not null)");
    jdbc.execute(
        "create table product_history_state (product_id bigint primary key references products(id), visibility varchar(20) not null)");
    jdbc.execute(
        "create table completion_documents (product_id bigint primary key references products(id), protected boolean not null)");
    jdbc.execute(
        "create table completion_drafts (product_id bigint primary key references products(id), owner_id bigint not null references users(id))");
  }

  private Fixture insertReservedFixture(Long sellerVersion, Long buyerVersion) {
    jdbc.update(
        "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (?, 'LAPTOP', 'maker', 'MAKER', 'model', 'p4-fingerprint', 'title', ?, 'ciphertext')",
        PRODUCT_ID,
        SELLER_ID);
    jdbc.update(
        "insert into listings (id, product_id, seller_id, title, price, condition_grade, description, status, published_at, source_draft_id, source_draft_version, active_slot) values (?, ?, ?, 'title', 1, 1, 'description', 'RESERVED', current_timestamp, 1, 0, 1)",
        LISTING_ID,
        PRODUCT_ID,
        SELLER_ID);
    long roomId = insertRoom(BUYER_ID);
    long otherRoomId = insertRoom(OTHER_BUYER_ID);
    long requestId =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status, transaction_status, seller_confirmed_version, buyer_confirmed_version) values (?, ?, 'ACCEPTED', 'RESERVED', ?, ?) returning id",
            Long.class,
            roomId,
            LISTING_ID,
            sellerVersion,
            buyerVersion);
    long otherRequestId =
        jdbc.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING') returning id",
            Long.class,
            otherRoomId,
            LISTING_ID);
    jdbc.update(
        "insert into trade_fixture_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, 'INTERNAL', 'PENDING')",
        PRODUCT_ID,
        requestId,
        SELLER_ID,
        BUYER_ID);
    jdbc.update("insert into product_history_state values (?, 'PUBLIC')", PRODUCT_ID);
    jdbc.update("insert into completion_documents values (?, false)", PRODUCT_ID);
    jdbc.update("insert into completion_drafts values (?, ?)", PRODUCT_ID, SELLER_ID);
    return new Fixture(requestId, otherRequestId, roomId, otherRoomId);
  }

  private void assertCompletedFixture(Fixture fixture) {
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("COMPLETED");
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("SOLD");
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("ACCEPTED");
    assertThat(
            jdbc.queryForObject(
                "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
        .isEqualTo(BUYER_ID);
    assertThat(
            value("select visibility from product_history_state where product_id = ?", PRODUCT_ID))
        .isEqualTo("PRIVATE");
    assertThat(
            value(
                "select request_status from purchase_requests where id = ?",
                fixture.otherRequestId()))
        .isEqualTo("REJECTED");
    assertThat(
            count(
                "select count(*) from audit_logs where action = 'TRADE_COMPLETED' and target_id = ?",
                fixture.requestId()))
        .isEqualTo(1L);
  }

  private void assertRolledBackLastConfirmation(Fixture fixture) {
    assertThat(
            value(
                "select transaction_status from purchase_requests where id = ?",
                fixture.requestId()))
        .isEqualTo("RESERVED");
    assertThat(value("select status from listings where id = ?", LISTING_ID)).isEqualTo("RESERVED");
    assertThat(
            jdbc.queryForObject(
                "select seller_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isEqualTo(FACTS_VERSION);
    assertThat(
            jdbc.queryForObject(
                "select buyer_confirmed_version from purchase_requests where id = ?",
                Long.class,
                fixture.requestId()))
        .isNull();
    assertThat(
            value(
                "select status from trade_fixture_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select owner_id from products where id = ?", Long.class, PRODUCT_ID))
        .isEqualTo(SELLER_ID);
    assertThat(
            value(
                "select request_status from purchase_requests where id = ?",
                fixture.otherRequestId()))
        .isEqualTo("PENDING");
    assertThat(
            count(
                "select count(*) from product_events pe join trade_fixture_transfers ot on ot.id = pe.transfer_request_id where ot.purchase_request_id = ?",
                fixture.requestId()))
        .isZero();
    assertThat(
            value("select visibility from product_history_state where product_id = ?", PRODUCT_ID))
        .isEqualTo("PUBLIC");
    assertThat(
            jdbc.queryForObject(
                "select protected from completion_documents where product_id = ?",
                Boolean.class,
                PRODUCT_ID))
        .isFalse();
    assertThat(
            count(
                "select count(*) from completion_drafts where product_id = ? and owner_id = ?",
                PRODUCT_ID,
                SELLER_ID))
        .isEqualTo(1L);
    assertThat(
            count(
                "select count(*) from audit_logs where action = 'TRADE_COMPLETED' and target_id = ?",
                fixture.requestId()))
        .isZero();
  }

  private TradeCompletionService completionService(
      PurchaseRequestRepository requestRepository, PublishedListingAccess listingAccess) {
    return new TradeCompletionService(
        requestRepository,
        rooms,
        listingAccess,
        products,
        details,
        changes,
        completionPort,
        audit,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @SuppressWarnings("unchecked")
  private HistoryService historyService(ApplicationEventPublisher events) {
    HistoryChangeReviewRecorder recorder =
        new ReservedHistoryChangeReviewRecorder(listings, requests, changes);
    ObjectProvider<HistoryChangeReviewRecorder> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(recorder);
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
        "키보드 수리",
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  private static ProductResponse currentProduct() {
    long version =
        jdbc.queryForObject(
            "select facts_version from products where id = ?", Long.class, PRODUCT_ID);
    return new ProductResponse(
        PRODUCT_ID,
        "title",
        "maker",
        "model",
        null,
        null,
        null,
        version,
        null,
        null,
        null,
        List.of(),
        null);
  }

  private static ListingDetailResponse officialDetail() {
    return new ListingDetailResponse(
        LISTING_ID,
        1L,
        ListingStatus.RESERVED,
        NOW,
        new ListingPreviewResponse.Content("title", "description", BigDecimal.ONE, 1),
        List.of(),
        new ListingPublicationResponse.Product(PRODUCT_ID, "maker", "model", null, null, null),
        new ListingPublicationResponse.History(
            ListingHistoryReader.State.READY,
            false,
            List.of(),
            new ListingPublicationResponse.Page(0, 20, 0, 0, false),
            0,
            0),
        true,
        null);
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

  private void runConcurrentlyAllowingConflict(Runnable first, Runnable second) throws Exception {
    runConcurrently(
        () -> runIgnoringExpectedConflict(first), () -> runIgnoringExpectedConflict(second));
  }

  private RaceOutcomes runConcurrentlyAllowingHistoryConflict(Runnable history, Runnable completion)
      throws Exception {
    AtomicReference<ErrorCode> historyFailure = new AtomicReference<>();
    AtomicReference<ErrorCode> completionFailure = new AtomicReference<>();
    runConcurrently(
        () -> historyFailure.set(runAllowingHistoryConflict(history)),
        () -> completionFailure.set(runAllowingCompletionConflict(completion)));
    return new RaceOutcomes(historyFailure.get(), completionFailure.get());
  }

  private static ErrorCode runAllowingHistoryConflict(Runnable operation) {
    try {
      operation.run();
      return null;
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT
          && exception.errorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
        throw exception;
      }
      return exception.errorCode();
    }
  }

  private static ErrorCode runAllowingCompletionConflict(Runnable operation) {
    try {
      operation.run();
      return null;
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT) {
        throw exception;
      }
      return exception.errorCode();
    }
  }

  static void runIgnoringExpectedConflict(Runnable operation) {
    try {
      operation.run();
    } catch (CommonException exception) {
      if (exception.errorCode() != ErrorCode.TRADE_CONFLICT) {
        throw exception;
      }
    }
  }

  private long insertRoom(long buyerId) {
    return jdbc.queryForObject(
        "insert into chat_rooms (listing_id, seller_id, inquirer_id) values (?, ?, ?) returning id",
        Long.class,
        LISTING_ID,
        SELLER_ID,
        buyerId);
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

  private record Fixture(long requestId, long otherRequestId, long roomId, long otherRoomId) {}

  private record RaceOutcomes(ErrorCode historyFailure, ErrorCode completionFailure) {}

  private static class FixtureCompletionPort implements InternalTradeCompletionPort {

    private final JdbcTemplate jdbc;
    private final AtomicBoolean failAfterWrites = new AtomicBoolean();

    private FixtureCompletionPort(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public void complete(Command command) {
      requireOne(
          jdbc.update(
              "update trade_fixture_transfers set status = 'ACCEPTED' where purchase_request_id = ? and product_id = ? and from_user_id = ? and to_user_id = ? and transfer_type = 'INTERNAL' and status = 'PENDING'",
              command.purchaseRequestId(),
              command.productId(),
              command.fromUserId(),
              command.toUserId()));
      requireOne(
          jdbc.update(
              "update products set owner_id = ? where id = ? and owner_id = ?",
              command.toUserId(),
              command.productId(),
              command.fromUserId()));
      requireOne(
          jdbc.update(
              "insert into product_events (product_id, event_type, occurred_on, title, transfer_request_id) select ?, 'TRANSFER', current_date, 'ownership transfer', id from trade_fixture_transfers where purchase_request_id = ? and product_id = ? and status = 'ACCEPTED'",
              command.productId(),
              command.purchaseRequestId(),
              command.productId()));
      requireOne(
          jdbc.update(
              "update product_history_state set visibility = 'PRIVATE' where product_id = ? and visibility = 'PUBLIC'",
              command.productId()));
      requireOne(
          jdbc.update(
              "update completion_documents set protected = true where product_id = ? and protected = false",
              command.productId()));
      requireOne(
          jdbc.update(
              "delete from completion_drafts where product_id = ? and owner_id = ?",
              command.productId(),
              command.fromUserId()));
      if (failAfterWrites.get()) throw new IllegalStateException("Injected completion failure");
    }
  }

  private static class FixtureCancellationPort implements InternalTransferCancellationPort {
    private final JdbcTemplate jdbc;

    private FixtureCancellationPort(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public void cancelPending(Command command) {
      requireOne(
          jdbc.update(
              "update trade_fixture_transfers set status = 'CANCELLED' where purchase_request_id = ? and product_id = ? and status = 'PENDING'",
              command.purchaseRequestId(),
              command.productId()));
    }
  }

  private static class FaultInjectingAuditRecorder implements TradeAuditRecorder {
    private final TradeAuditRecorder delegate;
    private final AtomicBoolean failAfterInsert = new AtomicBoolean();

    private FaultInjectingAuditRecorder(TradeAuditRecorder delegate) {
      this.delegate = delegate;
    }

    @Override
    public void reservationCancelled(
        long productId,
        long actorId,
        long purchaseRequestId,
        TradeCancellationReason reason,
        Instant cancelledAt) {
      delegate.reservationCancelled(productId, actorId, purchaseRequestId, reason, cancelledAt);
      failIfRequested();
    }

    @Override
    public void tradeCompleted(
        long productId,
        long actorId,
        long purchaseRequestId,
        long confirmedFactsVersion,
        Instant completedAt) {
      delegate.tradeCompleted(
          productId, actorId, purchaseRequestId, confirmedFactsVersion, completedAt);
      failIfRequested();
    }

    private void failIfRequested() {
      if (failAfterInsert.get()) throw new IllegalStateException("Injected audit failure");
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

  private static void requireOne(int updated) {
    if (updated != 1) throw new IllegalStateException("Expected exactly one fixture row");
  }
}
