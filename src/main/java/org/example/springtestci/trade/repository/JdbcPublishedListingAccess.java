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

  @Override
  public Optional<PublishedListing> findById(long listingId) {
    return queryOne(SELECT_LISTING, listingId);
  }

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

  private Optional<PublishedListing> queryOne(String sql, Object... parameters) {
    return jdbcTemplate.query(sql, this::mapListing, parameters).stream().findFirst();
  }

  private PublishedListing mapListing(ResultSet resultSet, int rowNumber) throws SQLException {
    return new PublishedListing(
        resultSet.getLong("id"),
        resultSet.getLong("product_id"),
        resultSet.getLong("seller_id"),
        resultSet.getLong("owner_id"),
        PublishedListingStatus.valueOf(resultSet.getString("status")));
  }
}
