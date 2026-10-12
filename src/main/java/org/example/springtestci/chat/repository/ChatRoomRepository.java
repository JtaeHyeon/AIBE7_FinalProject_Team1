package org.example.springtestci.chat.repository;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatRoomRepository {

  Optional<ChatRoom> findById(long roomId);

  Optional<ChatRoom> findByListingIdAndInquirerId(long listingId, long inquirerId);

  Page<ChatRoom> findByParticipantId(long participantId, Pageable pageable);

  ChatRoom create(long listingId, long sellerId, long inquirerId);
}
