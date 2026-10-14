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
    name = "chat_rooms",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_chat_rooms_listing_inquirer",
            columnNames = {"listing_id", "inquirer_id"}))
class ChatRoomEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "listing_id", nullable = false)
  private long listingId;

  @Column(name = "seller_id", nullable = false)
  private long sellerId;

  @Column(name = "inquirer_id", nullable = false)
  private long inquirerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ChatRoomEntity() {}

  ChatRoomEntity(long listingId, long sellerId, long inquirerId) {
    this.listingId = listingId;
    this.sellerId = sellerId;
    this.inquirerId = inquirerId;
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

  long listingId() {
    return listingId;
  }

  long sellerId() {
    return sellerId;
  }

  long inquirerId() {
    return inquirerId;
  }

  Instant createdAt() {
    return createdAt;
  }
}
