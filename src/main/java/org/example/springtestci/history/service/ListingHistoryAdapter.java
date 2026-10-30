package org.example.springtestci.history.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.service.ListingHistoryPrivatizer;
import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 누적 제품 이력을 판매글의 공개 이력 계약에 연결한다.
 *
 * <p>비용·문서·계정·감사 원문은 조회하지 않는다. 개인정보 검토가 완료되지 않은 자유 입력은 reviewed=false로 전달해 판매글 계층이 유형·발생일 같은 구조화
 * 정보만 표시하게 한다. 공개·비공개 변경은 호출자의 게시 또는 판매 중지 트랜잭션에 그대로 참여한다.
 */
@Component
@Profile("!sale-draft-demo")
@ConditionalOnMissingBean({
  ListingHistoryReader.class,
  ListingHistoryPublisher.class,
  ListingHistoryPrivatizer.class
})
public class ListingHistoryAdapter
    implements ListingHistoryReader, ListingHistoryPublisher, ListingHistoryPrivatizer {

  private final JdbcTemplate jdbc;

  public ListingHistoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 제품의 누적 이력과 공개가 허용된 정정 사유만 벌크 조회한다. */
  @Override
  public Snapshot read(long productId) {
    List<EventRow> rows =
        jdbc.query(
            """
            SELECT id, product_id, author_id, event_type, occurred_on, created_at, status,
                   title, content, service_provider, replaced_part, public_text_reviewed
              FROM product_events
             WHERE product_id = ?
             ORDER BY occurred_on, created_at, id
            """,
            this::eventRow,
            productId);
    Map<Long, List<Correction>> corrections = corrections(productId);
    List<Event> events =
        rows.stream()
            .map(
                row ->
                    new Event(
                        row.eventId(),
                        row.productId(),
                        row.authorId(),
                        EventType.valueOf(row.eventType()),
                        row.occurredOn(),
                        row.createdAt(),
                        EventStatus.valueOf(row.status()),
                        new PublicText(
                            row.title(),
                            row.content(),
                            row.serviceProvider(),
                            row.replacedPart(),
                            row.publicTextReviewed()),
                        corrections.getOrDefault(row.eventId(), List.of())))
            .toList();
    return new Snapshot(State.READY, false, events);
  }

  /** 게시글 생성과 같은 트랜잭션에서 현재 판매자의 누적 이력을 공개 상태로 바꾼다. */
  @Override
  public void publish(long productId, long listingId, long sellerId) {
    if (!matchesListingOwner(productId, listingId, sellerId)) unavailable();
    jdbc.update("UPDATE product_events SET visibility = 'PUBLIC' WHERE product_id = ?", productId);
  }

  /** 마지막 열린 판매글의 종료와 같은 트랜잭션에서 누적 이력을 비공개로 되돌린다. */
  @Override
  public void makePrivate(long productId, long listingId, long sellerId) {
    if (!matchesListingOwner(productId, listingId, sellerId) || hasOpenListing(productId)) {
      unavailable();
    }
    jdbc.update("UPDATE product_events SET visibility = 'PRIVATE' WHERE product_id = ?", productId);
  }

  private Map<Long, List<Correction>> corrections(long productId) {
    Map<Long, List<Correction>> result = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT id, event_id, created_at, reason, public_reason_reviewed
          FROM audit_logs
         WHERE product_id = ?
           AND event_id IS NOT NULL
           AND action IN ('HISTORY_CORRECTED', 'HISTORY_VOIDED')
         ORDER BY event_id, created_at, id
        """,
        rs -> {
          long eventId = rs.getLong("event_id");
          result
              .computeIfAbsent(eventId, ignored -> new ArrayList<>())
              .add(
                  new Correction(
                      rs.getLong("id"),
                      rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                      rs.getString("reason"),
                      rs.getBoolean("public_reason_reviewed")));
        },
        productId);
    return result;
  }

  private boolean matchesListingOwner(long productId, long listingId, long sellerId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
              FROM listings l
              JOIN products p ON p.id = l.product_id
             WHERE l.id = ?
               AND l.product_id = ?
               AND l.seller_id = ?
               AND p.owner_id = ?
            """,
            Integer.class,
            listingId,
            productId,
            sellerId,
            sellerId);
    return count != null && count == 1;
  }

  private boolean hasOpenListing(long productId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM listings WHERE product_id = ? AND status IN ('ACTIVE', 'RESERVED')",
            Integer.class,
            productId);
    return count != null && count > 0;
  }

  private EventRow eventRow(ResultSet row, int index) throws SQLException {
    return new EventRow(
        row.getLong("id"),
        row.getLong("product_id"),
        row.getObject("author_id", Long.class),
        row.getString("event_type"),
        row.getObject("occurred_on", java.time.LocalDate.class),
        row.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
        row.getString("status"),
        row.getString("title"),
        row.getString("content"),
        row.getString("service_provider"),
        row.getString("replaced_part"),
        row.getBoolean("public_text_reviewed"));
  }

  private void unavailable() {
    throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
  }

  private record EventRow(
      long eventId,
      long productId,
      Long authorId,
      String eventType,
      java.time.LocalDate occurredOn,
      Instant createdAt,
      String status,
      String title,
      String content,
      String serviceProvider,
      String replacedPart,
      boolean publicTextReviewed) {}
}
