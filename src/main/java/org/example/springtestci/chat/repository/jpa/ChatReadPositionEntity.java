package org.example.springtestci.chat.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "chat_read_positions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_chat_read_positions_room_user",
            columnNames = {"room_id", "user_id"}))
class ChatReadPositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false)
  private long roomId;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "last_read_message_id", nullable = false)
  private long lastReadMessageId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChatReadPositionEntity() {}

  ChatReadPositionEntity(long roomId, long userId, long lastReadMessageId) {
    this.roomId = roomId;
    this.userId = userId;
    this.lastReadMessageId = lastReadMessageId;
  }

  void update(long lastReadMessageId) {
    this.lastReadMessageId = lastReadMessageId;
    this.updatedAt = Instant.now();
  }

  @PrePersist
  void assignUpdatedAt() {
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
  }

  long roomId() {
    return roomId;
  }

  long userId() {
    return userId;
  }

  long lastReadMessageId() {
    return lastReadMessageId;
  }

  Instant updatedAt() {
    return updatedAt;
  }
}
