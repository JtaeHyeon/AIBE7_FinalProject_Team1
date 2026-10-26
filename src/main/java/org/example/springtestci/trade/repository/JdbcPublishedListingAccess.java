package org.example.springtestci.trade.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 제품을 먼저 잠근 뒤 게시 판매글을 읽고 ACTIVE/RESERVED 상태를 조건부 갱신하는 JDBC 어댑터다. */
@Repository
@ConditionalOnProperty(
    prefix = "app.features.trade-purchase-requests",
    name = "enabled",
    havingValue = "true")
public class JdbcPublishedListingAccess implements PublishedListingAccess {

  private static final String SELECT_LISTING =
      """
      SELECT listing.id, listing.product_id, listing.seller_id, product.owner_id,
             CASE WHEN product.archived_at IS NULL THEN listing.status ELSE 'CANCELLED' END AS status
        FROM listings listing
        JOIN products product ON product.id = listing.product_id
       WHERE listing.id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcPublishedListingAccess(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 잠금 없이 현재 제품 소유자와 게시 판매글 상태를 조회한다. */
  @Override
  public Optional<PublishedListing> findById(long listingId) {
    return queryOne(SELECT_LISTING, listingId);
  }

  /** 제품 행을 먼저 잠근 다음 같은 제품의 판매글을 잠가 거래 쓰기 간 잠금 순서를 통일한다. */
  @Override
  public Optional<PublishedListing> findByIdForUpdate(long listingId) {
    Optional<Long> productId =
        jdbcTemplate
            .queryForList("select product_id from listings where id = ?", Long.class, listingId)
            .stream()
            .findFirst();
    if (productId.isEmpty()) {
      return Optional.empty();
    }

    if (jdbcTemplate
        .queryForList(
            "select id from products where id = ? for update", Long.class, productId.get())
        .isEmpty()) {
      return Optional.empty();
    }

    return queryOne(
        SELECT_LISTING + " and listing.product_id = ? for update", listingId, productId.get());
  }

  /** 목록 응답용 판매글 상태를 한 번에 조회하며 보관 제품은 CANCELLED로 투영한다. */
  @Override
  public Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds) {
    if (listingIds.isEmpty()) {
      return Map.of();
    }

    String placeholders = listingIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
    return jdbcTemplate.query(
        """
        SELECT listing.id,
               CASE WHEN product.archived_at IS NULL THEN listing.status ELSE 'CANCELLED' END AS status
          FROM listings listing
          JOIN products product ON product.id = listing.product_id
         WHERE listing.id IN (
        """
            + placeholders
            + ")",
        resultSet -> {
          Map<Long, PublishedListingStatus> statuses = new LinkedHashMap<>();
          while (resultSet.next()) {
            statuses.put(
                resultSet.getLong("id"),
                PublishedListingStatus.valueOf(resultSet.getString("status")));
          }
          return statuses;
        },
        listingIds.toArray());
  }

  /** ACTIVE 한 행만 RESERVED로 바꾸고 영향 행이 정확히 하나가 아니면 경합으로 처리한다. */
  @Override
  public void reserve(long listingId) {
    int updated =
        jdbcTemplate.update(
            "update listings set status = 'RESERVED' where id = ? and status = 'ACTIVE'",
            listingId);
    if (updated != 1) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  /**
   * 현재 RESERVED인 판매글 한 건을 ACTIVE로 변경하고 영향 행 수를 검사한다.
   *
   * <p>동일 예약의 유효한 취소인지 확인하고 재시도가 새 예약을 해제하지 않도록 하는 책임은 호출 서비스에 있다.
   */
  @Override
  public void releaseReservation(long listingId) {
    int updated =
        jdbcTemplate.update(
            "update listings set status = 'ACTIVE' where id = ? and status = 'RESERVED'",
            listingId);
    if (updated != 1) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private Optional<PublishedListing> queryOne(String sql, Object... parameters) {
    return jdbcTemplate.query(sql, this::mapListing, parameters).stream().findFirst();
  }

  /** 제품 보관 여부를 반영한 판매글 상태와 현재 소유자를 거래 전용 사본으로 매핑한다. */
  private PublishedListing mapListing(ResultSet resultSet, int rowNumber) throws SQLException {
    return new PublishedListing(
        resultSet.getLong("id"),
        resultSet.getLong("product_id"),
        resultSet.getLong("seller_id"),
        resultSet.getLong("owner_id"),
        PublishedListingStatus.valueOf(resultSet.getString("status")));
  }
}
