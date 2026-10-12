package org.example.springtestci.chat.dto;

import java.time.Instant;

public record ChatMessageResponse(
    long messageId, long roomId, long senderId, String content, Instant createdAt) {

  @Override
  public String toString() {
    return "ChatMessageResponse[messageId="
        + messageId
        + ", roomId="
        + roomId
        + ", senderId="
        + senderId
        + ", content=<redacted>, createdAt="
        + createdAt
        + "]";
  }
}
