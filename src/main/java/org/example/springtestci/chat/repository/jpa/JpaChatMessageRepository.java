package org.example.springtestci.chat.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.example.springtestci.chat.domain.ChatMessage;
import org.example.springtestci.chat.repository.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaChatMessageRepository implements ChatMessageRepository {

  private final JpaChatMessageStore store;

  JpaChatMessageRepository(JpaChatMessageStore store) {
    this.store = store;
  }

  @Override
  public Page<ChatMessage> findByRoomId(long roomId, Pageable pageable) {
    return store.findByRoomId(roomId, pageable).map(this::toDomain);
  }

  @Override
  public Optional<ChatMessage> findById(long messageId) {
    return store.findById(messageId).map(this::toDomain);
  }

  @Override
  public List<ChatMessage> findLatestByRoomIds(Collection<Long> roomIds) {
    if (roomIds.isEmpty()) {
      return List.of();
    }
    return store.findLatestByRoomIds(roomIds).stream().map(this::toDomain).toList();
  }

  @Override
  public Set<Long> findRoomIdsWithUnreadMessages(Collection<Long> roomIds, long readerId) {
    if (roomIds.isEmpty()) {
      return Set.of();
    }
    return store.findRoomIdsWithUnreadMessages(roomIds, readerId);
  }

  @Override
  public ChatMessage append(long roomId, long senderId, String content) {
    return toDomain(store.save(new ChatMessageEntity(roomId, senderId, content)));
  }

  private ChatMessage toDomain(ChatMessageEntity entity) {
    return new ChatMessage(
        entity.id(), entity.roomId(), entity.senderId(), entity.content(), entity.createdAt());
  }
}
