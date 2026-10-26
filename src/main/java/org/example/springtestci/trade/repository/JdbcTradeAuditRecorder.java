package org.example.springtestci.trade.repository;

import java.time.Instant;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.service.TradeAuditRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** 기존 `audit_logs`에 예약 취소 사실을 기록하며 삽입 실패를 거래 트랜잭션에 전파한다. */
@Repository
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class JdbcTradeAuditRecorder implements TradeAuditRecorder {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcTradeAuditRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /** 예약 취소 후 상태를 JSON 사본으로 직렬화하고 기존 감사 테이블에 정확히 한 행을 삽입한다. */
  @Override
  public void reservationCancelled(
      long productId,
      long actorId,
      long purchaseRequestId,
      TradeCancellationReason reason,
      Instant cancelledAt) {
    String afterFacts =
        objectMapper.writeValueAsString(
            new CancellationFacts("CANCELLED", "CANCELLED", cancelledAt));
    int inserted =
        jdbcTemplate.update(
            "insert into audit_logs (product_id, actor_id, target_id, action, after_facts, reason) values (?, ?, ?, 'TRADE_RESERVATION_CANCELLED', ?, ?)",
            productId,
            actorId,
            purchaseRequestId,
            afterFacts,
            reason.name());
    if (inserted != 1) {
      throw new IllegalStateException("Trade audit insert affected an unexpected row count");
    }
  }

  @Override
  public void tradeCompleted(
      long productId,
      long actorId,
      long purchaseRequestId,
      long confirmedFactsVersion,
      Instant completedAt) {
    String afterFacts =
        objectMapper.writeValueAsString(
            new CompletionFacts(
                "ACCEPTED", "COMPLETED", "SOLD", confirmedFactsVersion, completedAt));
    int inserted =
        jdbcTemplate.update(
            "insert into audit_logs (product_id, actor_id, target_id, action, after_facts) values (?, ?, ?, 'TRADE_COMPLETED', ?)",
            productId,
            actorId,
            purchaseRequestId,
            afterFacts);
    if (inserted != 1) {
      throw new IllegalStateException("Trade audit insert affected an unexpected row count");
    }
  }

  private record CancellationFacts(
      String requestStatus, String transactionStatus, Instant cancelledAt) {}

  private record CompletionFacts(
      String requestStatus,
      String transactionStatus,
      String listingStatus,
      long confirmedFactsVersion,
      Instant completedAt) {}
}
