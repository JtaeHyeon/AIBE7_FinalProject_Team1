package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingTradeCanceller;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.service.InternalTransferCancellationPort;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.TradeCancellationService;
import org.example.springtestci.trade.service.TradeListingCanceller;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

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
  private static TradeCancellationService cancellations;
  private static ReservationChangeService changes;
  private static TradeListingCanceller listingCanceller;

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
    createExternalFixtureTables();
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
    var rooms = new JdbcRoomRepository(jdbc);
    transfers = new TestTransferCancellation(jdbc);
    var json = JsonMapper.builder().build();
    var audit = new JdbcTradeAuditRecorder(jdbc, json);
    reviews = new JdbcReservationChangeReviewRepository(jdbc, json);
    cancellations =
        new TradeCancellationService(
            requests, rooms, listings, transfers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    changes =
        new ReservationChangeService(
            requests, reviews, rooms, listings, cancellations, Clock.fixed(NOW, ZoneOffset.UTC));
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
        "truncate table transaction_change_reviews, ownership_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, product_events, products, users, audit_logs restart identity cascade");
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
                "select status from ownership_transfers where purchase_request_id = ?",
                fixture.requestId()))
        .isEqualTo("PENDING");
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
    jdbc.update("update listings set status = 'CANCELLED' where id = ?", LISTING_ID);

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

  private static void createExternalFixtureTables() {
    jdbc.execute(
        "create table product_events (id bigint primary key, product_id bigint not null references products(id))");
    jdbc.execute(
        "create table ownership_transfers (id bigint generated by default as identity primary key, product_id bigint not null references products(id), purchase_request_id bigint not null unique references purchase_requests(id), from_user_id bigint not null references users(id), to_user_id bigint not null references users(id), transfer_type varchar(20) not null, status varchar(20) not null)");
    jdbc.execute(
        "create unique index uq_p3_fixture_pending_transfer on ownership_transfers(product_id) where status = 'PENDING'");
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
        "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, 'INTERNAL', 'PENDING')",
        PRODUCT_ID,
        requestId,
        SELLER_ID,
        BUYER_ID);
    return new Fixture(requestId, roomId);
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
    jdbc.update("insert into product_events (id, product_id) values (?, ?)", eventId, PRODUCT_ID);
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
              "update ownership_transfers set status = 'CANCELLED' where product_id = ? and purchase_request_id = ? and from_user_id = ? and to_user_id = ? and transfer_type = 'INTERNAL' and status = 'PENDING'",
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
