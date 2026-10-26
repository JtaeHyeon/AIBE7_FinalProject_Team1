package org.example.springtestci.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.example.springtestci.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 채팅 메시지 저장, 페이지 조회와 방별 최신·미읽음 계산을 제공하는 영속성 포트다. */
public interface ChatMessageRepository {

  /** 방의 메시지를 호출자가 지정한 정렬·페이지 조건으로 조회한다. */
  Page<ChatMessage> findByRoomId(long roomId, Pageable pageable);

  Optional<ChatMessage> findById(long messageId);

  /** 각 방의 최신 메시지를 목록 화면 조합용으로 일괄 조회한다. */
  List<ChatMessage> findLatestByRoomIds(Collection<Long> roomIds);

  /** 사용자 읽음 위치보다 뒤의 메시지가 존재하는 방 ID 집합을 반환한다. */
  Set<Long> findRoomIdsWithUnreadMessages(Collection<Long> roomIds, long readerId);

  /** 방 ID와 인증 발신자 ID로 메시지를 저장한다. */
  ChatMessage append(long roomId, long senderId, String content);
}
