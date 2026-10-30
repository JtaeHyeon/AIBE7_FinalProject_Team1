package org.example.springtestci.ownership.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.service.InternalTransferCancellationPort.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** SQL 조건별 대상 제한과 롤백을 검증한다. 정식 제약·서비스 연결은 PostgreSQL HTTP 시험에서 별도로 검증한다. */
class JdbcInternalTransferCancellationTest {
  private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
  private SingleConnectionDataSource source;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private JdbcInternalTransferCancellation adapter;
  private final Command command = new Command(101, 21, 1, 2);

  @BeforeEach
  void start() {
    source =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:transfer_cancel_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute(
        "CREATE TABLE ownership_transfers(id BIGINT PRIMARY KEY,product_id BIGINT,purchase_request_id BIGINT,from_user_id BIGINT,to_user_id BIGINT,transfer_type VARCHAR(20),status VARCHAR(20),pending_slot SMALLINT,cancelled_at TIMESTAMP WITH TIME ZONE,token_hash VARCHAR(64),expires_at TIMESTAMP WITH TIME ZONE)");
    jdbc.update(
        "INSERT INTO ownership_transfers VALUES(31,101,21,1,2,'INTERNAL','PENDING',1,NULL,'preserved-token',TIMESTAMP WITH TIME ZONE '2026-10-31 00:00:00+00')");
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    adapter = new JdbcInternalTransferCancellation(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void stop() {
    source.destroy();
  }

  @Test
  void cancelsOnlyTheExactInternalPendingTransferAndKeepsItsTokenAndExpiry() {
    var before = row();
    transactions.executeWithoutResult(ignored -> adapter.cancelPending(command));
    var after = row();
    assertEquals("CANCELLED", after.get("status"));
    assertNull(after.get("pending_slot"));
    assertEquals(NOW, ((OffsetDateTime) after.get("cancelled_at")).toInstant());
    for (String key :
        List.of(
            "product_id",
            "purchase_request_id",
            "from_user_id",
            "to_user_id",
            "token_hash",
            "expires_at",
            "transfer_type")) assertEquals(before.get(key), after.get(key));
  }

  @Test
  void aDifferentProductRequestSellerOrBuyerNeverMatches() {
    var before = row();
    for (var other :
        List.of(
            new Command(201, 21, 1, 2),
            new Command(101, 22, 1, 2),
            new Command(101, 21, 3, 2),
            new Command(101, 21, 1, 3))) {
      var error =
          assertThrows(
              CommonException.class,
              () -> transactions.executeWithoutResult(ignored -> adapter.cancelPending(other)));
      assertEquals(ErrorCode.TRADE_CONFLICT, error.errorCode());
      assertEquals(before, row());
    }
  }

  @Test
  void aDirectTransferCannotBeCancelledByTheTradePort() {
    jdbc.update("UPDATE ownership_transfers SET transfer_type='DIRECT'");
    assertConflictWithoutChanges();
  }

  @Test
  void completedCancelledAndExpiredTransfersKeepTheirOriginalState() {
    for (String status : List.of("COMPLETED", "CANCELLED", "EXPIRED")) {
      jdbc.update(
          "UPDATE ownership_transfers SET status=?,pending_slot=NULL,cancelled_at=?",
          status,
          java.sql.Timestamp.from(NOW.minusSeconds(60)));
      assertConflictWithoutChanges();
    }
  }

  @Test
  void stalePendingTransfersCanStillBeCleanedUpWhenTheirReservationEnds() {
    jdbc.update(
        "UPDATE ownership_transfers SET expires_at=?",
        java.sql.Timestamp.from(NOW.minusSeconds(60)));
    transactions.executeWithoutResult(ignored -> adapter.cancelPending(command));
    assertEquals("CANCELLED", row().get("status"));
  }

  @Test
  void downstreamFailureRollsBackTheTransferAndItsSlot() {
    var before = row();
    assertThrows(
        IllegalStateException.class,
        () ->
            transactions.executeWithoutResult(
                ignored -> {
                  adapter.cancelPending(command);
                  throw new IllegalStateException("failure after transfer cancellation");
                }));
    assertEquals(before, row());
  }

  @Test
  void missingSchemaReportsDependencyFailureInsteadOfSuccess() {
    jdbc.execute("DROP TABLE ownership_transfers");
    var error =
        assertThrows(
            CommonException.class,
            () -> transactions.executeWithoutResult(ignored -> adapter.cancelPending(command)));
    assertEquals(ErrorCode.TRADE_DEPENDENCY_UNAVAILABLE, error.errorCode());
  }

  private java.util.Map<String, Object> row() {
    return jdbc.queryForMap("SELECT * FROM ownership_transfers WHERE id=31");
  }

  private void assertConflictWithoutChanges() {
    var before = row();
    var error =
        assertThrows(
            CommonException.class,
            () -> transactions.executeWithoutResult(ignored -> adapter.cancelPending(command)));
    assertEquals(ErrorCode.TRADE_CONFLICT, error.errorCode());
    assertEquals(before, row());
  }
}
