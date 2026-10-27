package org.example.springtestci.ai.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.springtestci.ai.entity.ListingSummaryState;
import org.example.springtestci.ai.entity.ListingSummaryState.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** listings의 요약 컬럼만 JDBC로 변경한다. JPA의 판매 조건 저장이 요약 상태를 덮어쓰지 않게 분리한다. */
@Repository
public class ListingSummaryRepository {
  private final JdbcTemplate jdbc;

  public ListingSummaryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 기존 판매글처럼 요약 컬럼이 비어 있으면 미요청 상태로 읽는다. */
  public ListingSummaryState read(long id) {
    return jdbc.queryForObject(
        "SELECT summary_status,summary_text,summary_source_product_version,summary_source_listing_version,summary_attempt_id,summary_error_code,summary_updated_at FROM listings WHERE id=?",
        (row, n) ->
            new ListingSummaryState(
                row.getString(1) == null ? Status.NOT_REQUESTED : Status.valueOf(row.getString(1)),
                row.getString(2),
                (Long) row.getObject(3),
                (Long) row.getObject(4),
                row.getObject(5, UUID.class),
                row.getString(6),
                row.getTimestamp(7) == null ? null : row.getTimestamp(7).toInstant()),
        id);
  }

  /** 호출자는 제품 → 판매글 잠금을 보유한다. 새로운 시도는 이전 텍스트를 공개 상태에서 제거한다. */
  public void queue(long id, long productVersion, long listingVersion, UUID attempt, Instant now) {
    jdbc.update(
        "UPDATE listings SET summary_status='QUEUED',summary_text=NULL,summary_source_product_version=?,summary_source_listing_version=?,summary_attempt_id=?,summary_error_code=NULL,summary_updated_at=? WHERE id=?",
        productVersion,
        listingVersion,
        attempt,
        Timestamp.from(now),
        id);
  }

  /** 같은 시도 ID의 대기 작업을 처리 중으로 바꾼 한 작업자만 true를 받는다. */
  public boolean claim(Pending pending, Instant now) {
    return jdbc.update(
            "UPDATE listings SET summary_status='PROCESSING',summary_updated_at=? WHERE id=? AND summary_attempt_id=? AND summary_status='QUEUED' AND status IN ('ACTIVE','RESERVED')",
            Timestamp.from(now),
            pending.listingId(),
            pending.attemptId())
        == 1;
  }

  /** 현재 처리 중인 시도만 완료로 저장한다. 이미 종료되었거나 시도가 바뀐 글은 갱신하지 않는다. */
  public void ready(Pending pending, String text, Instant now) {
    jdbc.update(
        "UPDATE listings SET summary_status='READY',summary_text=?,summary_error_code=NULL,summary_updated_at=? WHERE id=? AND summary_attempt_id=? AND summary_status='PROCESSING' AND status IN ('ACTIVE','RESERVED')",
        text,
        Timestamp.from(now),
        pending.listingId(),
        pending.attemptId());
  }

  /** 시도 번호가 바뀐 뒤 도착한 이전 작업의 오류는 새 결과를 덮어쓰지 않는다. */
  public void fail(Pending pending, String code, Instant now) {
    jdbc.update(
        "UPDATE listings SET summary_status='FAILED',summary_text=NULL,summary_error_code=?,summary_updated_at=? WHERE id=? AND summary_attempt_id=? AND summary_status IN ('QUEUED','PROCESSING')",
        code,
        Timestamp.from(now),
        pending.listingId(),
        pending.attemptId());
  }

  /** 오래 대기한 작업부터 최대 10건을 읽어 한 번에 가져오는 작업량을 제한한다. */
  public List<Pending> pending() {
    return jdbc.query(
        "SELECT id,summary_attempt_id FROM listings WHERE summary_status='QUEUED' ORDER BY summary_updated_at,id LIMIT 10",
        (row, n) -> new Pending(row.getLong(1), row.getObject(2, UUID.class)));
  }

  /** 서버 재시작·강제 종료로 남은 작업을 안전한 실패로 바꿔 영구 생성 중 상태를 막는다. */
  public void expire(Instant processingBefore, Instant queuedBefore, Instant now) {
    jdbc.update(
        "UPDATE listings SET summary_status='FAILED',summary_text=NULL,summary_error_code='AI_002',summary_updated_at=? WHERE (summary_status='PROCESSING' AND summary_updated_at<?) OR (summary_status='QUEUED' AND summary_updated_at<?)",
        Timestamp.from(now),
        Timestamp.from(processingBefore),
        Timestamp.from(queuedBefore));
  }

  public record Pending(long listingId, UUID attemptId) {}
}
