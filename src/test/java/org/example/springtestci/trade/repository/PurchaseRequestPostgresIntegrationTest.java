package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository.PageResult;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.PurchaseRequestService;
import org.example.springtestci.trade.support.PendingTransferConflictTranslator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "P2_TRADE_TEST_ENABLED", matches = "true")
class PurchaseRequestPostgresIntegrationTest {

  private static final long SELLER_ID = 1L;
  private static final long BUYER_ONE_ID = 2L;
  private static final long BUYER_TWO_ID = 3L;
  private static final String SCHEMA = "p2_trade_" + UUID.randomUUID().toString().replace("-", "");

  private static DataSource adminDataSource;
  private static DataSource schemaDataSource;
  private static JdbcTemplate jdbcTemplate;
  private static TransactionTemplate transactionTemplate;
  private static JdbcPurchaseRequestRepository requestRepository;
  private static FaultInjectingListingAccess listingAccess;
  private static JdbcTransferWriter transferWriter;
  private static PurchaseRequestService service;

  @BeforeAll
  static void setUpDatabase() {
    String url = requiredEnvironment("P2_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P2_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P2_TRADE_TEST_DB_PASSWORD");
    adminDataSource = new DriverManagerDataSource(url, user, password);
    new JdbcTemplate(adminDataSource).execute("create schema " + SCHEMA);

    schemaDataSource = new DriverManagerDataSource(withCurrentSchema(url, SCHEMA), user, password);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbcTemplate = new JdbcTemplate(schemaDataSource);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration", "classpath:db/pending/trade-p2")
        .outOfOrder(true)
        .load()
        .migrate();
    createOwnershipTransferFixtureTable();

    transactionTemplate =
        new TransactionTemplate(new DataSourceTransactionManager(schemaDataSource));
    requestRepository = new JdbcPurchaseRequestRepository(jdbcTemplate);
    listingAccess = new FaultInjectingListingAccess(new JdbcPublishedListingAccess(jdbcTemplate));
    transferWriter = new JdbcTransferWriter(jdbcTemplate);
    service =
        new PurchaseRequestService(
            requestRepository, new JdbcRoomRepository(jdbcTemplate), listingAccess, transferWriter);
  }

  @AfterAll
  static void tearDownDatabase() {
    if (adminDataSource != null) {
      new JdbcTemplate(adminDataSource).execute("drop schema " + SCHEMA + " cascade");
    }
  }

  @BeforeEach
  void resetFixtures() {
    jdbcTemplate.execute(
        "truncate table ownership_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, products, users restart identity cascade");
    insertUser(SELLER_ID, "seller@example.test", "seller");
    insertUser(BUYER_ONE_ID, "buyer1@example.test", "buyer1");
    insertUser(BUYER_TWO_ID, "buyer2@example.test", "buyer2");
    transferWriter.failAfterInsert.set(false);
    listingAccess.failAfterReserve.set(false);
  }

  @Test
  void commitsAcceptedReservationAndInternalPendingTransfer() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long requestId = createRequest(fixture);

    var response = transactionTemplate.execute(status -> service.accept(requestId, SELLER_ID));

    assertThat(response.requestStatus()).isEqualTo(PurchaseRequestStatus.ACCEPTED);
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, fixture.listingId()))
        .isEqualTo("RESERVED");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ownership_transfers where purchase_request_id = ? and transfer_type = 'INTERNAL' and status = 'PENDING' and from_user_id = ? and to_user_id = ? and token_hash is null and recipient_approved_at is null and owner_approved_at is null",
                Long.class,
                requestId,
                SELLER_ID,
                BUYER_ONE_ID))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject("select owner_id from products where id = 1", Long.class))
        .isEqualTo(SELLER_ID);
  }

  @Test
  void cancellingAndRejectingOtherPendingRequestsPreservesExistingReservation() {
    Fixture selected = insertFixture(BUYER_ONE_ID);
    long selectedRequestId = createRequest(selected);
    long buyerTwoRoomId = insertRoom(selected.listingId(), BUYER_TWO_ID);
    long buyerTwoRequestId = createRequest(new Fixture(selected.listingId(), buyerTwoRoomId));
    insertUser(4L, "buyer3@example.test", "buyer3");
    long buyerThreeRoomId = insertRoom(selected.listingId(), 4L);
    long buyerThreeRequestId = createRequest(new Fixture(selected.listingId(), buyerThreeRoomId));
    transactionTemplate.execute(status -> service.accept(selectedRequestId, SELLER_ID));

    transactionTemplate.execute(status -> service.cancel(buyerTwoRequestId, BUYER_TWO_ID));
    transactionTemplate.execute(status -> service.reject(buyerThreeRequestId, SELLER_ID));

    assertThat(requestRepository.findById(selectedRequestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.ACCEPTED);
    assertThat(requestRepository.findById(buyerTwoRequestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.CANCELLED);
    assertThat(requestRepository.findById(buyerThreeRequestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.REJECTED);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, selected.listingId()))
        .isEqualTo("RESERVED");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ownership_transfers where product_id = 1 and status = 'PENDING'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void createsNewPendingRecordAfterCancellationAndPreservesHistory() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long cancelledRequestId = createRequest(fixture);
    transactionTemplate.execute(status -> service.cancel(cancelledRequestId, BUYER_ONE_ID));

    long newRequestId = createRequest(fixture);

    assertThat(newRequestId).isNotEqualTo(cancelledRequestId);
    assertThat(requestRepository.findById(cancelledRequestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.CANCELLED);
    assertThat(requestRepository.findById(newRequestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where room_id = ?",
                Long.class,
                fixture.roomId()))
        .isEqualTo(2L);
  }

  @Test
  void filtersSentAndReceivedRequestsByStatusRoomAndPage() {
    Fixture buyerOne = insertFixture(BUYER_ONE_ID);
    long cancelledRequestId = createRequest(buyerOne);
    transactionTemplate.execute(status -> service.cancel(cancelledRequestId, BUYER_ONE_ID));
    long buyerTwoRoomId = insertRoom(buyerOne.listingId(), BUYER_TWO_ID);
    long pendingRequestId = createRequest(new Fixture(buyerOne.listingId(), buyerTwoRoomId));

    PageResult sent =
        requestRepository.findAll(
            BUYER_ONE_ID,
            PurchaseRequestDirection.SENT,
            PurchaseRequestStatus.CANCELLED,
            buyerOne.roomId(),
            0,
            1);
    PageResult received =
        requestRepository.findAll(
            SELLER_ID,
            PurchaseRequestDirection.RECEIVED,
            PurchaseRequestStatus.PENDING,
            null,
            0,
            1);

    assertThat(sent.totalElements()).isEqualTo(1);
    assertThat(sent.items()).extracting(PurchaseRequest::id).containsExactly(cancelledRequestId);
    assertThat(received.totalElements()).isEqualTo(1);
    assertThat(received.items()).extracting(PurchaseRequest::id).containsExactly(pendingRequestId);
  }

  @Test
  void rollsBackAllAcceptanceChangesWhenTransferInsertFails() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long requestId = createRequest(fixture);
    transferWriter.failAfterInsert.set(true);

    assertThatThrownBy(
            () -> transactionTemplate.execute(status -> service.accept(requestId, SELLER_ID)))
        .isInstanceOf(IllegalStateException.class);

    PurchaseRequest stored = requestRepository.findById(requestId).orElseThrow();
    assertThat(stored.requestStatus()).isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(stored.transactionStatus()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, fixture.listingId()))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isZero();
  }

  @Test
  void rollsBackRequestAndListingWhenListingReservationFails() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long requestId = createRequest(fixture);
    listingAccess.failAfterReserve.set(true);

    assertThatThrownBy(
            () -> transactionTemplate.execute(status -> service.accept(requestId, SELLER_ID)))
        .isInstanceOf(IllegalStateException.class);

    PurchaseRequest stored = requestRepository.findById(requestId).orElseThrow();
    assertThat(stored.requestStatus()).isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(stored.transactionStatus()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, fixture.listingId()))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isZero();
  }

  @Test
  void concurrentCreatesReturnSamePendingRequest() throws Exception {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Long> first = executor.submit(() -> createConcurrently(fixture, ready, start));
      Future<Long> second = executor.submit(() -> createConcurrently(fixture, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(first.get()).isEqualTo(second.get());
    } finally {
      executor.shutdownNow();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where room_id = ? and request_status = 'PENDING'",
                Long.class,
                fixture.roomId()))
        .isEqualTo(1L);
  }

  @Test
  void allowsOnlyOneConcurrentAcceptanceForSameListing() throws Exception {
    Fixture first = insertFixture(BUYER_ONE_ID);
    long secondRoomId = insertRoom(first.listingId(), BUYER_TWO_ID);
    long firstRequestId = createRequest(first);
    long secondRequestId = createRequest(new Fixture(first.listingId(), secondRoomId));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> firstResult =
          executor.submit(() -> acceptConcurrently(firstRequestId, ready, start));
      Future<Boolean> secondResult =
          executor.submit(() -> acceptConcurrently(secondRequestId, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(firstResult.get(), secondResult.get()))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      executor.shutdownNow();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where transaction_status = 'RESERVED'",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where request_status = 'PENDING'",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ownership_transfers where status = 'PENDING'", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void concurrentAcceptAndCancelLeaveOneConsistentResult() throws Exception {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long requestId = createRequest(fixture);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> accepted = executor.submit(() -> acceptConcurrently(requestId, ready, start));
      Future<Boolean> cancelled =
          executor.submit(() -> cancelConcurrently(requestId, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(accepted.get(), cancelled.get())).containsExactlyInAnyOrder(true, false);
    } finally {
      executor.shutdownNow();
    }

    PurchaseRequest stored = requestRepository.findById(requestId).orElseThrow();
    String listingStatus =
        jdbcTemplate.queryForObject(
            "select status from listings where id = ?", String.class, fixture.listingId());
    Long transferCount =
        jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class);
    if (stored.requestStatus() == PurchaseRequestStatus.ACCEPTED) {
      assertThat(stored.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
      assertThat(listingStatus).isEqualTo("RESERVED");
      assertThat(transferCount).isEqualTo(1L);
    } else {
      assertThat(stored.requestStatus()).isEqualTo(PurchaseRequestStatus.CANCELLED);
      assertThat(stored.transactionStatus()).isNull();
      assertThat(listingStatus).isEqualTo("ACTIVE");
      assertThat(transferCount).isZero();
    }
  }

  @Test
  void databaseRejectsInvalidStatePairAndDuplicatePendingRoom() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    createRequest(fixture);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into purchase_requests (room_id, listing_id, request_status, transaction_status) values (?, ?, 'PENDING', 'RESERVED')",
                    fixture.roomId(),
                    fixture.listingId()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into purchase_requests (room_id, listing_id, request_status) values (?, ?, 'PENDING')",
                    fixture.roomId(),
                    fixture.listingId()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void databaseRejectsDuplicateReservedListingAndMissingForeignKeys() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long firstRequestId = createRequest(fixture);
    long secondRoomId = insertRoom(fixture.listingId(), BUYER_TWO_ID);
    long secondRequestId = createRequest(new Fixture(fixture.listingId(), secondRoomId));
    jdbcTemplate.update(
        "update purchase_requests set request_status = 'ACCEPTED', transaction_status = 'RESERVED' where id = ?",
        firstRequestId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update purchase_requests set request_status = 'ACCEPTED', transaction_status = 'RESERVED' where id = ?",
                    secondRequestId))
        .isInstanceOfSatisfying(
            DataAccessException.class,
            exception ->
                assertThat(exception.getMostSpecificCause().getMessage())
                    .contains("uq_purchase_requests_reserved_listing"));
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into purchase_requests (room_id, listing_id, request_status) values (999999, ?, 'PENDING')",
                    fixture.listingId()))
        .isInstanceOfSatisfying(
            DataAccessException.class,
            exception -> {
              assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
              assertThat(((SQLException) exception.getMostSpecificCause()).getSQLState())
                  .isEqualTo("23503");
            });
  }

  @Test
  void databaseEnforcesEveryPurchaseRequestStatePairIncludingAcceptedNull() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long requestId = createRequest(fixture);

    assertStatusPairConstraint(
        () ->
            jdbcTemplate.update(
                "insert into purchase_requests (room_id, listing_id, request_status, transaction_status) values (?, ?, 'ACCEPTED', null)",
                fixture.roomId(),
                fixture.listingId()));
    assertStatusPairConstraint(
        () ->
            jdbcTemplate.update(
                "update purchase_requests set request_status = 'ACCEPTED', transaction_status = null where id = ?",
                requestId));

    assertThat(
            jdbcTemplate.update(
                "update purchase_requests set request_status = 'REJECTED', transaction_status = null where id = ?",
                requestId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "update purchase_requests set request_status = 'ACCEPTED', transaction_status = 'RESERVED' where id = ?",
                requestId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "update purchase_requests set transaction_status = 'COMPLETED' where id = ?",
                requestId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "update purchase_requests set request_status = 'CANCELLED', transaction_status = null where id = ?",
                requestId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "update purchase_requests set transaction_status = 'CANCELLED' where id = ?",
                requestId))
        .isEqualTo(1);
  }

  @Test
  void rejectsCreateAndAcceptWhenProductOwnerDiffersFromListingSeller() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    jdbcTemplate.update("update products set owner_id = ? where id = 1", BUYER_TWO_ID);

    assertThatThrownBy(
            () ->
                transactionTemplate.execute(
                    status -> service.create(fixture.listingId(), fixture.roomId(), BUYER_ONE_ID)))
        .isInstanceOf(CommonException.class);
    assertThat(jdbcTemplate.queryForObject("select count(*) from purchase_requests", Long.class))
        .isZero();

    jdbcTemplate.update("update products set owner_id = ? where id = 1", SELLER_ID);
    long requestId = createRequest(fixture);
    jdbcTemplate.update("update products set owner_id = ? where id = 1", BUYER_TWO_ID);

    assertThatThrownBy(
            () -> transactionTemplate.execute(status -> service.accept(requestId, SELLER_ID)))
        .isInstanceOf(CommonException.class);
    assertThat(requestRepository.findById(requestId).orElseThrow().requestStatus())
        .isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, fixture.listingId()))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isZero();
  }

  @Test
  void readsProductionListingStateFromProductsAndListings() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);

    PublishedListing listing = listingAccess.findById(fixture.listingId()).orElseThrow();

    assertThat(listing.productId()).isEqualTo(1L);
    assertThat(listing.sellerId()).isEqualTo(SELLER_ID);
    assertThat(listing.currentOwnerId()).isEqualTo(SELLER_ID);
    assertThat(listing.status()).isEqualTo(PublishedListingStatus.ACTIVE);
    assertThat(listingAccess.findStatuses(List.of())).isEmpty();
    assertThat(listingAccess.findStatuses(List.of(fixture.listingId(), 999L)))
        .containsExactly(Map.entry(fixture.listingId(), PublishedListingStatus.ACTIVE));

    jdbcTemplate.update("update products set owner_id = ? where id = 1", BUYER_TWO_ID);
    assertThat(listingAccess.findById(fixture.listingId()).orElseThrow().currentOwnerId())
        .isEqualTo(BUYER_TWO_ID);

    jdbcTemplate.update("update products set archived_at = current_timestamp where id = 1");
    assertThat(listingAccess.findById(fixture.listingId()).orElseThrow().status())
        .isEqualTo(PublishedListingStatus.CANCELLED);
    assertThat(listingAccess.findStatuses(List.of(fixture.listingId())))
        .containsExactly(Map.entry(fixture.listingId(), PublishedListingStatus.CANCELLED));
  }

  @Test
  void rollsBackAcceptanceWhenProductAlreadyHasPendingTransfer() {
    Fixture fixture = insertFixture(BUYER_ONE_ID);
    long targetRequestId = createRequest(fixture);
    long otherRoomId = insertRoom(fixture.listingId(), BUYER_TWO_ID);
    long otherRequestId = createRequest(new Fixture(fixture.listingId(), otherRoomId));
    jdbcTemplate.update(
        "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (1, ?, ?, ?, 'INTERNAL', 'PENDING')",
        otherRequestId,
        SELLER_ID,
        BUYER_TWO_ID);

    assertThatThrownBy(
            () -> transactionTemplate.execute(status -> service.accept(targetRequestId, SELLER_ID)))
        .isInstanceOfSatisfying(
            CommonException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRADE_CONFLICT));

    PurchaseRequest stored = requestRepository.findById(targetRequestId).orElseThrow();
    assertThat(stored.requestStatus()).isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(stored.transactionStatus()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from listings where id = ?", String.class, fixture.listingId()))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ownership_transfers where product_id = 1 and status = 'PENDING'",
                Long.class))
        .isEqualTo(1L);
  }

  private void assertStatusPairConstraint(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            DataAccessException.class,
            exception -> {
              assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
              SQLException sqlException = (SQLException) exception.getMostSpecificCause();
              assertThat(sqlException.getSQLState()).isEqualTo("23514");
              assertThat(sqlException.getMessage()).contains("ck_purchase_requests_status_pair");
            });
  }

  private boolean acceptConcurrently(long requestId, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await(5, TimeUnit.SECONDS);
    try {
      transactionTemplate.execute(status -> service.accept(requestId, SELLER_ID));
      return true;
    } catch (CommonException exception) {
      return false;
    }
  }

  private boolean cancelConcurrently(long requestId, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await(5, TimeUnit.SECONDS);
    try {
      transactionTemplate.execute(status -> service.cancel(requestId, BUYER_ONE_ID));
      return true;
    } catch (CommonException exception) {
      return false;
    }
  }

  private long createConcurrently(Fixture fixture, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await(5, TimeUnit.SECONDS);
    return createRequest(fixture);
  }

  private long createRequest(Fixture fixture) {
    return transactionTemplate
        .execute(
            status ->
                service.create(
                    fixture.listingId(), fixture.roomId(), buyerForRoom(fixture.roomId())))
        .response()
        .requestId();
  }

  private long buyerForRoom(long roomId) {
    return jdbcTemplate.queryForObject(
        "select inquirer_id from chat_rooms where id = ?", Long.class, roomId);
  }

  private Fixture insertFixture(long buyerId) {
    jdbcTemplate.update(
        "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (1, 'LAPTOP', 'maker', 'MAKER', 'model', 'fingerprint', 'title', ?, 'ciphertext')",
        SELLER_ID);
    jdbcTemplate.update(
        "insert into listings (id, product_id, seller_id, title, price, condition_grade, description, status, published_at, source_draft_id, source_draft_version, active_slot) values (1, 1, ?, 'title', 1, 1, 'description', 'ACTIVE', current_timestamp, 1, 0, 1)",
        SELLER_ID);
    return new Fixture(1L, insertRoom(1L, buyerId));
  }

  private long insertRoom(long listingId, long buyerId) {
    return jdbcTemplate.queryForObject(
        "insert into chat_rooms (listing_id, seller_id, inquirer_id) values (?, ?, ?) returning id",
        Long.class,
        listingId,
        SELLER_ID,
        buyerId);
  }

  private void insertUser(long id, String email, String nickname) {
    jdbcTemplate.update(
        "insert into users (id, email, password_hash, nickname, role, created_at, updated_at) values (?, ?, 'hash', ?, 'USER', ?, ?)",
        id,
        email,
        nickname,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
  }

  private static void createOwnershipTransferFixtureTable() {
    jdbcTemplate.execute(
        """
        create table ownership_transfers (
          id bigint generated by default as identity primary key,
          product_id bigint not null references products(id),
          purchase_request_id bigint not null unique references purchase_requests(id),
          from_user_id bigint not null references users(id),
          to_user_id bigint not null references users(id),
          transfer_type varchar(20) not null,
          status varchar(20) not null,
          token_hash varchar(255),
          recipient_approved_at timestamp with time zone,
          owner_approved_at timestamp with time zone,
          created_at timestamp with time zone not null default current_timestamp
        )
        """);
    jdbcTemplate.execute(
        "create unique index uq_ownership_transfers_pending_product on ownership_transfers (product_id) where status = 'PENDING'");
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

  private record Fixture(long listingId, long roomId) {}

  private static class FaultInjectingListingAccess implements PublishedListingAccess {

    private final PublishedListingAccess delegate;
    private final AtomicBoolean failAfterReserve = new AtomicBoolean();

    private FaultInjectingListingAccess(PublishedListingAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<PublishedListing> findById(long listingId) {
      return delegate.findById(listingId);
    }

    @Override
    public Optional<PublishedListing> findByIdForUpdate(long listingId) {
      return delegate.findByIdForUpdate(listingId);
    }

    @Override
    public Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds) {
      return delegate.findStatuses(listingIds);
    }

    @Override
    public void reserve(long listingId) {
      delegate.reserve(listingId);
      if (failAfterReserve.get()) {
        throw new IllegalStateException("Injected listing reservation failure");
      }
    }

    @Override
    public void releaseReservation(long listingId) {
      delegate.releaseReservation(listingId);
    }
  }

  private static class JdbcTransferWriter implements InternalTransferPendingWriter {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean failAfterInsert = new AtomicBoolean();

    private JdbcTransferWriter(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createPending(Command command) {
      int inserted;
      try {
        inserted =
            jdbcTemplate.update(
                "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, ?, ?)",
                command.productId(),
                command.purchaseRequestId(),
                command.fromUserId(),
                command.toUserId(),
                command.transferType(),
                command.status());
      } catch (DataAccessException exception) {
        throw PendingTransferConflictTranslator.translate(exception);
      }
      if (inserted != 1) {
        throw new IllegalStateException(
            "Ownership transfer insert affected an unexpected row count");
      }
      if (failAfterInsert.get()) {
        throw new IllegalStateException("Injected transfer failure");
      }
    }
  }

  private static class JdbcRoomRepository implements ChatRoomRepository {

    private final JdbcTemplate jdbcTemplate;

    private JdbcRoomRepository(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ChatRoom> findById(long roomId) {
      return jdbcTemplate
          .query(
              "select id, listing_id, seller_id, inquirer_id, created_at from chat_rooms where id = ?",
              (resultSet, rowNumber) ->
                  new ChatRoom(
                      resultSet.getLong("id"),
                      resultSet.getLong("listing_id"),
                      resultSet.getLong("seller_id"),
                      resultSet.getLong("inquirer_id"),
                      resultSet.getTimestamp("created_at").toInstant()),
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
