package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 서비스 계층에 JPA 엔티티를 노출하지 않는 채팅방 영속성 포트다. */
public interface ChatRoomRepository {

  /** 채팅방 식별자로 도메인 모델을 조회한다. */
  Optional<ChatRoom> findById(long roomId);

  /** 한 판매글의 같은 문의자에게 이미 생성된 방을 조회해 방 생성을 멱등하게 한다. */
  Optional<ChatRoom> findByListingIdAndInquirerId(long listingId, long inquirerId);

  /** 판매자 또는 문의자로 참여한 방만 페이지로 조회한다. */
  Page<ChatRoom> findByParticipantId(long participantId, Pageable pageable);

  /** 서비스의 읽기 트랜잭션에서 참여자와 판매글 조건을 목록·총건수에 동일하게 적용한다. */
  Page<ChatRoom> findByParticipantIdAndListingId(
      long participantId, long listingId, Pageable pageable);

  /** 게시 판매글 ID, 판매자 ID와 문의자 ID를 연결한 방을 생성한다. */
  ChatRoom create(long listingId, long sellerId, long inquirerId);
}
