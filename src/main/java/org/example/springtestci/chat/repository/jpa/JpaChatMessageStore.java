package org.example.springtestci.chat.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaChatMessageStore extends JpaRepository<ChatMessageEntity, Long> {

  Page<ChatMessageEntity> findByRoomId(long roomId, Pageable pageable);

  @Query(
      """
      select message
      from ChatMessageEntity message
      where message.roomId in :roomIds
        and not exists (
          select newer.id
          from ChatMessageEntity newer
          where newer.roomId = message.roomId
            and (
              newer.createdAt > message.createdAt
              or (newer.createdAt = message.createdAt and newer.id > message.id)
            )
        )
      """)
  List<ChatMessageEntity> findLatestByRoomIds(@Param("roomIds") Collection<Long> roomIds);

  @Query(
      """
      select distinct message.roomId
      from ChatMessageEntity message
      left join ChatReadPositionEntity position
        on position.roomId = message.roomId and position.userId = :readerId
      left join ChatMessageEntity lastRead
        on lastRead.id = position.lastReadMessageId
      where message.roomId in :roomIds
        and message.senderId <> :readerId
        and (
          position.id is null
          or message.createdAt > lastRead.createdAt
          or (message.createdAt = lastRead.createdAt and message.id > lastRead.id)
        )
      """)
  Set<Long> findRoomIdsWithUnreadMessages(
      @Param("roomIds") Collection<Long> roomIds, @Param("readerId") long readerId);
}
