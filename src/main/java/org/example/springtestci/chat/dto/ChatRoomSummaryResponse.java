package org.example.springtestci.chat.dto;

import java.time.Instant;
import org.example.springtestci.chat.domain.ChatListingStatus;

public record ChatRoomSummaryResponse(
    long roomId,
    long listingId,
    String counterpartyNickname,
    String listingTitle,
    ChatListingStatus listingStatus,
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
        + ", listingStatus="
        + listingStatus
        + ", latestMessagePreview=<redacted>, latestMessageAt="
        + latestMessageAt
        + ", hasUnreadMessages="
        + hasUnreadMessages
        + "]";
  }
}
