package org.example.springtestci.chat.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
class ChatMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false)
  private long roomId;

  @Column(name = "sender_id", nullable = false)
  private long senderId;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ChatMessageEntity() {}

  ChatMessageEntity(long roomId, long senderId, String content) {
    this.roomId = roomId;
    this.senderId = senderId;
    this.content = content;
  }

  @PrePersist
  void assignCreatedAt() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  Long id() {
    return id;
  }

  long roomId() {
    return roomId;
  }

  long senderId() {
    return senderId;
  }

  String content() {
    return content;
  }

  Instant createdAt() {
    return createdAt;
  }
}
