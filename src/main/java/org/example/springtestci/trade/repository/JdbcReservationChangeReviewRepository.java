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

/** JSONB 변경 사본을 불변 저장하고 결정 행 잠금을 제공하는 P3 JDBC 어댑터다. */
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

  /** 변경 사본을 잠금 없이 조회해 중복 등록 여부를 확인한다. */
  @Override
  public Optional<ReservationChangeReview> find(long purchaseRequestId, long eventId) {
    return queryOne(
        SELECT_REVIEW + " where purchase_request_id = ? and event_id = ?",
        purchaseRequestId,
        eventId);
  }

  /** 결정 경합을 직렬화하기 위해 복합 키의 review 행을 `FOR UPDATE`로 조회한다. */
  @Override
  public Optional<ReservationChangeReview> findForUpdate(long purchaseRequestId, long eventId) {
    return queryOne(
        SELECT_REVIEW + " where purchase_request_id = ? and event_id = ? for update",
        purchaseRequestId,
        eventId);
  }

  /** 복합 키 충돌 시 기존 JSON 사본을 덮어쓰지 않고 {@code false}를 반환한다. */
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

  /** 아직 미응답인 행 하나만 결정·시각으로 갱신하고 영향 행이 다르면 경합으로 처리한다. */
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

  /** 사건 ID 오름차순으로 변경 사본을 조회하고 전체 건수를 함께 반환한다. */
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

  /** 거래 완료 가드와 API 표시를 위한 미응답 행 수를 반환한다. */
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

  /** JSONB 사본을 JsonNode로 복원하고 미응답 행의 결정·시각 NULL 쌍을 그대로 보존한다. */
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
