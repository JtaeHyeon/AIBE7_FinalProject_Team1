package org.example.springtestci.chat.repository.jpa;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaChatRoomRepository implements ChatRoomRepository {

  private final JpaChatRoomStore store;

  JpaChatRoomRepository(JpaChatRoomStore store) {
    this.store = store;
  }

  @Override
  public Optional<ChatRoom> findById(long roomId) {
    return store.findById(roomId).map(this::toDomain);
  }

  @Override
  public Optional<ChatRoom> findByListingIdAndInquirerId(long listingId, long inquirerId) {
    return store.findByListingIdAndInquirerId(listingId, inquirerId).map(this::toDomain);
  }

  @Override
  public Page<ChatRoom> findByParticipantId(long participantId, Pageable pageable) {
    return store.findByParticipantId(participantId, pageable).map(this::toDomain);
  }

  @Override
  public ChatRoom create(long listingId, long sellerId, long inquirerId) {
    return toDomain(store.save(new ChatRoomEntity(listingId, sellerId, inquirerId)));
  }

  private ChatRoom toDomain(ChatRoomEntity entity) {
    return new ChatRoom(
        entity.id(),
        entity.listingId(),
        entity.sellerId(),
        entity.inquirerId(),
        entity.createdAt());
  }
}
