package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatReadPosition;

/** 사용자별 방 읽음 위치를 단조 증가 방식으로 관리하는 영속성 포트다. */
public interface ChatReadPositionRepository {

  Optional<ChatReadPosition> findByRoomIdAndUserId(long roomId, long userId);

  ChatReadPosition update(long roomId, long userId, long lastReadMessageId);
}
