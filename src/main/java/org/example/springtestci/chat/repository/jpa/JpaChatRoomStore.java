package org.example.springtestci.chat.repository.jpa;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaChatRoomStore extends JpaRepository<ChatRoomEntity, Long> {

  Optional<ChatRoomEntity> findByListingIdAndInquirerId(long listingId, long inquirerId);

  @Query(
      value =
          """
          select room
          from ChatRoomEntity room
          where room.sellerId = :participantId or room.inquirerId = :participantId
          order by coalesce(
            (select max(message.createdAt) from ChatMessageEntity message where message.roomId = room.id),
            room.createdAt
          ) desc, room.id desc
          """,
      countQuery =
          """
          select count(room)
          from ChatRoomEntity room
          where room.sellerId = :participantId or room.inquirerId = :participantId
          """)
  Page<ChatRoomEntity> findByParticipantId(
      @Param("participantId") long participantId, Pageable pageable);
}
