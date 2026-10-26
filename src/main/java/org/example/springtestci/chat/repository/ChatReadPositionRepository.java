package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatReadPosition;

/** 사용자별 방 읽음 위치를 단조 증가 방식으로 관리하는 영속성 포트다. */
public interface ChatReadPositionRepository {

  /** 방·사용자 복합 식별자로 현재 읽음 위치를 조회한다. */
  Optional<ChatReadPosition> findByRoomIdAndUserId(long roomId, long userId);

  /** 마지막 읽은 메시지 ID를 기존 값보다 뒤로만 이동시킨다. */
  ChatReadPosition update(long roomId, long userId, long lastReadMessageId);
}
