package org.example.springtestci.chat.dto;

import java.time.Instant;

public record ChatRoomSummaryResponse(
    long roomId,
    long listingId,
    String counterpartyNickname,
    String listingTitle,
    String latestMessagePreview,
    Instant latestMessageAt,
    boolean hasUnreadMessages) {

  @Override
  public String toString() {
    return "ChatRoomSummaryResponse[roomId="
        + roomId
        + ", listingId="
        + listingId
        + ", counterpartyNickname="
        + counterpartyNickname
        + ", listingTitle="
        + listingTitle
        + ", latestMessagePreview=<redacted>, latestMessageAt="
        + latestMessageAt
        + ", hasUnreadMessages="
        + hasUnreadMessages
        + "]";
  }
}
