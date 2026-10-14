package org.example.springtestci.chat.repository.jpa;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatReadPosition;
import org.example.springtestci.chat.repository.ChatReadPositionRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaChatReadPositionRepository implements ChatReadPositionRepository {

  private final JpaChatReadPositionStore store;

  JpaChatReadPositionRepository(JpaChatReadPositionStore store) {
    this.store = store;
  }

  @Override
  public Optional<ChatReadPosition> findByRoomIdAndUserId(long roomId, long userId) {
    return store.findByRoomIdAndUserId(roomId, userId).map(this::toDomain);
  }

  @Override
  public ChatReadPosition update(long roomId, long userId, long lastReadMessageId) {
    ChatReadPositionEntity entity =
        store
            .findByRoomIdAndUserId(roomId, userId)
            .orElseGet(() -> new ChatReadPositionEntity(roomId, userId, lastReadMessageId));
    entity.update(lastReadMessageId);
    return toDomain(store.save(entity));
  }

  private ChatReadPosition toDomain(ChatReadPositionEntity entity) {
    return new ChatReadPosition(
        entity.roomId(), entity.userId(), entity.lastReadMessageId(), entity.updatedAt());
  }
}
