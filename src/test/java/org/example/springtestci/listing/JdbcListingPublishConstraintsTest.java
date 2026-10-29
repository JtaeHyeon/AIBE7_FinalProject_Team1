package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.example.springtestci.listing.repository.JdbcListingPublishConstraints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 최소 원천 스키마로 상태·제품 범위·만료 경계와 미배포 원천의 보수적 처리를 검증한다. */
class JdbcListingPublishConstraintsTest {
  private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
  private SingleConnectionDataSource source;
  private JdbcTemplate jdbc;
  private JdbcListingPublishConstraints constraints;

  @BeforeEach
  void start() {
    source =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:publish_constraints_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE listings(id BIGINT PRIMARY KEY, product_id BIGINT)");
    jdbc.execute(
        "CREATE TABLE purchase_requests(listing_id BIGINT, transaction_status VARCHAR(20))");
    jdbc.execute(
        "CREATE TABLE ownership_transfers(product_id BIGINT,status VARCHAR(20),expires_at TIMESTAMP WITH TIME ZONE)");
    constraints = new JdbcListingPublishConstraints(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    jdbc.update("INSERT INTO listings VALUES(11,101),(21,201)");
  }

  @AfterEach
  void stop() {
    source.destroy();
  }

  @Test
  void emptyReadySourcesDoNotBlockPublishing() {
    var state = constraints.inspect(101);
    assertTrue(state.ready());
    assertFalse(state.ongoingTrade());
    assertFalse(state.pendingTransfer());
  }

  @Test
  void reservedTradeIsLookedUpThroughEveryListingForTheProduct() {
    jdbc.update("INSERT INTO purchase_requests VALUES(11,'RESERVED')");
    assertTrue(constraints.inspect(101).ongoingTrade());
    assertFalse(constraints.inspect(201).ongoingTrade());
  }

  @Test
  void pendingPurchaseAndEndedTransactionsAreNotOngoingTrades() {
    jdbc.update(
        "INSERT INTO purchase_requests VALUES(11,NULL),(11,'COMPLETED'),(11,'CANCELLED'),(21,'RESERVED')");
    assertFalse(constraints.inspect(101).ongoingTrade());
  }

  @Test
  void pendingTransferIsValidThroughItsExactExpiryAndCannotBlockAnotherProduct() {
    transfer(101, "PENDING", NOW);
    assertTrue(constraints.inspect(101).pendingTransfer());
    assertFalse(constraints.inspect(201).pendingTransfer());
  }

  @Test
  void expiredPendingAndEndedTransfersDoNotBlockOrGetRewritten() {
    transfer(101, "PENDING", NOW.minusSeconds(1));
    for (String state : new String[] {"COMPLETED", "CANCELLED", "EXPIRED"})
      transfer(101, state, NOW.plusSeconds(3600));
    assertFalse(constraints.inspect(101).pendingTransfer());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM ownership_transfers WHERE status='PENDING'", Integer.class));
  }

  @Test
  void bothIndependentSourcesCanBlockTheSameProduct() {
    jdbc.update("INSERT INTO purchase_requests VALUES(11,'RESERVED')");
    transfer(101, "PENDING", NOW.plusSeconds(3600));
    var state = constraints.inspect(101);
    assertTrue(state.ongoingTrade());
    assertTrue(state.pendingTransfer());
  }

  @Test
  void missingEitherSourceIsUnavailableInsteadOfAssumingNoTrade() {
    for (String table : new String[] {"purchase_requests", "ownership_transfers"}) {
      jdbc.execute("ALTER TABLE " + table + " RENAME TO unavailable_source");
      assertFalse(constraints.inspect(101).ready());
      assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
      jdbc.execute("ALTER TABLE unavailable_source RENAME TO " + table);
    }
  }

  @Test
  void anIncompleteSourceBecomesReadyAfterItsMissingColumnIsDeployed() {
    jdbc.execute("ALTER TABLE ownership_transfers DROP COLUMN expires_at");
    assertFalse(constraints.inspect(101).ready());
    jdbc.execute("ALTER TABLE ownership_transfers ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE");
    assertTrue(constraints.inspect(101).ready());
  }

  private void transfer(long productId, String state, Instant expiry) {
    jdbc.update(
        "INSERT INTO ownership_transfers VALUES(?,?,?)", productId, state, Timestamp.from(expiry));
  }
}
