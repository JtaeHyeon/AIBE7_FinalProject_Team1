package org.example.springtestci.chat.repository.jpa;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 운영 JPA 조회 계약이다. 참여자 조건과 최근 활동·ID 정렬을 유지하며 서비스 트랜잭션에 참여한다. */
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

  /** 판매글 필터를 페이지·count 양쪽에 적용하며 메시지 조인으로 방 수를 늘리지 않는다. */
  @Query(
      value =
          """
          select room
          from ChatRoomEntity room
          where (room.sellerId = :participantId or room.inquirerId = :participantId)
            and room.listingId = :listingId
          order by coalesce(
            (select max(message.createdAt) from ChatMessageEntity message where message.roomId = room.id),
            room.createdAt
          ) desc, room.id desc
          """,
      countQuery =
          """
          select count(room)
          from ChatRoomEntity room
          where (room.sellerId = :participantId or room.inquirerId = :participantId)
            and room.listingId = :listingId
          """)
  Page<ChatRoomEntity> findByParticipantIdAndListingId(
      @Param("participantId") long participantId,
      @Param("listingId") long listingId,
      Pageable pageable);
}
