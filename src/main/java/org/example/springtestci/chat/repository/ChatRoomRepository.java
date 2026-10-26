package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 서비스 계층에 JPA 엔티티를 노출하지 않는 채팅방 영속성 포트다. */
public interface ChatRoomRepository {

  Optional<ChatRoom> findById(long roomId);

  Optional<ChatRoom> findByListingIdAndInquirerId(long listingId, long inquirerId);

  Page<ChatRoom> findByParticipantId(long participantId, Pageable pageable);

  ChatRoom create(long listingId, long sellerId, long inquirerId);
}
