package org.example.springtestci.chat.repository;

import org.example.springtestci.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatMessageRepository {

  Page<ChatMessage> findByRoomId(long roomId, Pageable pageable);

  ChatMessage append(long roomId, long senderId, String content);
}
