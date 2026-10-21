package org.example.springtestci.listing.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ListingAuditRepository {
  private final JdbcTemplate jdbc;

  public ListingAuditRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void published(long productId, long userId, long listingId) {
    jdbc.update(
        "INSERT INTO audit_logs(product_id, actor_id, target_id, action) VALUES (?, ?, ?, 'LISTING_PUBLISHED')",
        productId,
        userId,
        listingId);
  }
}
