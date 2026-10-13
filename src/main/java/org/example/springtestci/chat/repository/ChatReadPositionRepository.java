package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatReadPosition;

public interface ChatReadPositionRepository {

  Optional<ChatReadPosition> findByRoomIdAndUserId(long roomId, long userId);

  ChatReadPosition update(long roomId, long userId, long lastReadMessageId);
}
