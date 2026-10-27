package org.example.springtestci.listing.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 게시·수정·중지의 처리 이력을 audit_logs에 남긴다. 호출 서비스의 트랜잭션에 함께 반영된다. */
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

  public void changed(
      long productId,
      long userId,
      long listingId,
      String action,
      String before,
      String after,
      String reason) {
    jdbc.update(
        "INSERT INTO audit_logs(product_id, actor_id, target_id, action, before_facts, after_facts, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
        productId,
        userId,
        listingId,
        action,
        before,
        after,
        reason);
  }
}
