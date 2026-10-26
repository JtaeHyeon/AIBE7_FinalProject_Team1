package org.example.springtestci.trade.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.ReservationChangeReview;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class JdbcReservationChangeReviewRepository implements ReservationChangeReviewRepository {

  private static final String SELECT_REVIEW =
      "select purchase_request_id, event_id, facts_version, change_facts::text, decision, reviewed_at from transaction_change_reviews";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcReservationChangeReviewRepository(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReservationChangeReview> find(long purchaseRequestId, long eventId) {
    return queryOne(
        SELECT_REVIEW + " where purchase_request_id = ? and event_id = ?",
        purchaseRequestId,
        eventId);
  }

  @Override
  public Optional<ReservationChangeReview> findForUpdate(long purchaseRequestId, long eventId) {
    return queryOne(
        SELECT_REVIEW + " where purchase_request_id = ? and event_id = ? for update",
        purchaseRequestId,
        eventId);
  }

  @Override
  public boolean saveIfAbsent(ReservationChangeReview review) {
    int inserted =
        jdbcTemplate.update(
            "insert into transaction_change_reviews (purchase_request_id, event_id, facts_version, change_facts) values (?, ?, ?, ?::jsonb) on conflict (purchase_request_id, event_id) do nothing",
            review.purchaseRequestId(),
            review.eventId(),
            review.factsVersion(),
            objectMapper.writeValueAsString(review.changeFacts()));
    return inserted == 1;
  }

  @Override
  public void updateDecision(ReservationChangeReview review) {
    int updated =
        jdbcTemplate.update(
            "update transaction_change_reviews set decision = ?, reviewed_at = ? where purchase_request_id = ? and event_id = ? and decision is null",
            review.decision().name(),
            Timestamp.from(review.reviewedAt()),
            review.purchaseRequestId(),
            review.eventId());
    if (updated != 1) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  @Override
  public PageResult findAll(long purchaseRequestId, int page, int size) {
    Long total =
        jdbcTemplate.queryForObject(
            "select count(*) from transaction_change_reviews where purchase_request_id = ?",
            Long.class,
            purchaseRequestId);
    var items =
        jdbcTemplate.query(
            SELECT_REVIEW + " where purchase_request_id = ? order by event_id limit ? offset ?",
            this::mapReview,
            purchaseRequestId,
            size,
            (long) page * size);
    return new PageResult(items, total == null ? 0 : total);
  }

  @Override
  public long countUnreviewed(long purchaseRequestId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from transaction_change_reviews where purchase_request_id = ? and decision is null",
            Long.class,
            purchaseRequestId);
    return count == null ? 0 : count;
  }

  private Optional<ReservationChangeReview> queryOne(String sql, Object... parameters) {
    return jdbcTemplate.query(sql, this::mapReview, parameters).stream().findFirst();
  }

  private ReservationChangeReview mapReview(ResultSet resultSet, int rowNumber)
      throws SQLException {
    String decision = resultSet.getString("decision");
    Timestamp reviewedAt = resultSet.getTimestamp("reviewed_at");
    return ReservationChangeReview.restore(
        resultSet.getLong("purchase_request_id"),
        resultSet.getLong("event_id"),
        resultSet.getLong("facts_version"),
        objectMapper.readTree(resultSet.getString("change_facts")),
        decision == null ? null : ChangeReviewDecision.valueOf(decision),
        reviewedAt == null ? null : reviewedAt.toInstant());
  }
}
