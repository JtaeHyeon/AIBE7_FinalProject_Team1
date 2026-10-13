package org.example.springtestci.chat.domain;

import java.time.Instant;

public record ChatMessage(
    long messageId, long roomId, long senderId, String content, Instant createdAt) {

  @Override
  public String toString() {
    return "ChatMessage[messageId="
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
