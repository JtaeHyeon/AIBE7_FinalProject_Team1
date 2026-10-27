package org.example.springtestci.chat.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatListingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 게시된 판매글만 조회한다. 비공개 초안의 번호를 판매글 번호로 해석하지 않는다. */
@Component
public class JdbcChatListingReader implements ChatListingReader {

  private static final String SELECT_LISTING =
      """
      SELECT l.id, l.seller_id, l.product_id, l.title, l.status, p.owner_id, p.archived_at
        FROM listings l JOIN products p ON p.id = l.product_id
       WHERE l.id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcChatListingReader(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<ChatListingSnapshot> findById(long listingId) {
    return jdbcTemplate.query(SELECT_LISTING, this::toSnapshot, listingId).stream().findFirst();
  }

  ChatListingSnapshot toSnapshot(ResultSet row, int index) throws SQLException {
    ChatListingStatus status =
        row.getObject("archived_at") == null
            ? ChatListingStatus.valueOf(row.getString("status"))
            : ChatListingStatus.CANCELLED;
    return new ChatListingSnapshot(
        row.getLong("id"),
        row.getLong("seller_id"),
        row.getLong("product_id"),
        row.getLong("owner_id"),
        status,
        row.getString("title"));
  }
}
