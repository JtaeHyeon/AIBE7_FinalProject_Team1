package org.example.springtestci.chat.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatListingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 공개 판매글 테이블이 생기기 전까지 listing_drafts를 판매글로 간주하는 임시 구현. */
@Component
public class JdbcChatListingReader implements ChatListingReader {

  private static final String SELECT_LISTING =
      """
      SELECT d.id, d.author_id, d.product_id, p.owner_id, p.archived_at, p.title AS product_title,
             d.title AS draft_title
        FROM listing_drafts d JOIN products p ON p.id = d.product_id
       WHERE d.id = ?
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
    String draftTitle = row.getString("draft_title");
    String title =
        draftTitle != null && !draftTitle.isBlank() ? draftTitle : row.getString("product_title");
    ChatListingStatus status =
        row.getObject("archived_at") == null
            ? ChatListingStatus.ACTIVE
            : ChatListingStatus.CANCELLED;
    return new ChatListingSnapshot(
        row.getLong("id"),
        row.getLong("author_id"),
        row.getLong("product_id"),
        row.getLong("owner_id"),
        status,
        title);
  }
}
