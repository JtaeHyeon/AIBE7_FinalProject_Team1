package org.example.springtestci.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.example.springtestci.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatMessageRepository {

  Page<ChatMessage> findByRoomId(long roomId, Pageable pageable);

  Optional<ChatMessage> findById(long messageId);

  List<ChatMessage> findLatestByRoomIds(Collection<Long> roomIds);

  Set<Long> findRoomIdsWithUnreadMessages(Collection<Long> roomIds, long readerId);

  ChatMessage append(long roomId, long senderId, String content);
}
