package org.example.springtestci.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatListingStatus;
import org.junit.jupiter.api.Test;

class JdbcChatListingReaderTest {

  private final JdbcChatListingReader reader = new JdbcChatListingReader(null);

  @Test
  void 게시된_판매글의_제목과_판매자를_쓴다() throws Exception {
    ResultSet row = row("초안 제목", "상품 제목", null);

    ChatListingSnapshot snapshot = reader.toSnapshot(row, 0);

    assertEquals("초안 제목", snapshot.title());
    assertEquals(ChatListingStatus.ACTIVE, snapshot.status());
    assertEquals(7L, snapshot.sellerId());
    assertEquals(7L, snapshot.currentOwnerId());
  }

  @Test
  void 예약_상태를_판매중으로_바꾸지_않는다() throws Exception {
    ResultSet row = row("판매글 제목", "상품 제목", null);
    when(row.getString("status")).thenReturn("RESERVED");
    assertEquals(ChatListingStatus.RESERVED, reader.toSnapshot(row, 0).status());
  }

  @Test
  void 상품이_보관되면_취소_상태다() throws Exception {
    ResultSet row = row("초안 제목", "상품 제목", OffsetDateTime.now());

    assertEquals(ChatListingStatus.CANCELLED, reader.toSnapshot(row, 0).status());
  }

  private ResultSet row(String draftTitle, String productTitle, Object archivedAt)
      throws Exception {
    ResultSet row = mock(ResultSet.class);
    when(row.getString("title")).thenReturn(draftTitle);
    when(row.getString("status")).thenReturn("ACTIVE");
    when(row.getObject("archived_at")).thenReturn(archivedAt);
    when(row.getLong("id")).thenReturn(1L);
    when(row.getLong("seller_id")).thenReturn(7L);
    when(row.getLong("product_id")).thenReturn(3L);
    when(row.getLong("owner_id")).thenReturn(7L);
    return row;
  }
}
