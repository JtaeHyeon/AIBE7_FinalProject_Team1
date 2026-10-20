package org.example.springtestci.listing.repository;

import org.example.springtestci.listing.dto.ListingDraftSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ListingDraftQueryRepository {
  private static final String OWNED_DRAFTS =
      """
      FROM listing_drafts d JOIN products p ON p.id = d.product_id
      WHERE d.author_id = ? AND p.owner_id = ? AND p.archived_at IS NULL AND p.category = 'LAPTOP'
      """;
  private final JdbcTemplate jdbc;

  public ListingDraftQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 페이지를 자르기 전에 현재 소유권을 검사한다. 사진·본문을 읽지 않아 목록의 N+1을 방지한다. */
  public Page<ListingDraftSummaryResponse> findAvailableTo(long userId, Pageable pageable) {
    long total = jdbc.queryForObject("SELECT count(*) " + OWNED_DRAFTS, Long.class, userId, userId);
    var items =
        jdbc.query(
            """
        SELECT d.id, d.product_id, p.title AS product_title, d.title, d.price,
               d.condition_grade, d.version, d.updated_at
        """
                + OWNED_DRAFTS
                + " ORDER BY d.updated_at DESC, d.id DESC LIMIT ? OFFSET ?",
            (row, index) ->
                new ListingDraftSummaryResponse(
                    row.getLong("id"),
                    row.getLong("product_id"),
                    row.getString("product_title"),
                    row.getString("title"),
                    row.getBigDecimal("price"),
                    row.getObject("condition_grade", Integer.class),
                    row.getLong("version"),
                    row.getTimestamp("updated_at").toInstant()),
            userId,
            userId,
            pageable.getPageSize(),
            pageable.getOffset());
    return new PageImpl<>(items, pageable, total);
  }
}
