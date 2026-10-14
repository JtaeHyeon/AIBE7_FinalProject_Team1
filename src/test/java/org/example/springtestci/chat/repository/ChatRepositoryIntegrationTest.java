package org.example.springtestci.chat.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.springtestci.chat.domain.ChatMessage;
import org.example.springtestci.chat.domain.ChatReadPosition;
import org.example.springtestci.chat.domain.ChatRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.features.chat-api.enabled=false")
@ActiveProfiles("local")
@Transactional
class ChatRepositoryIntegrationTest {

  @Autowired private ChatRoomRepository roomRepository;
  @Autowired private ChatMessageRepository messageRepository;
  @Autowired private ChatReadPositionRepository readPositionRepository;

  @Test
  void storesRoomsMessagesAndPersonalReadPositions() {
    ChatRoom room = roomRepository.create(100L, 10L, 20L);
    ChatMessage buyerMessage = messageRepository.append(room.roomId(), 20L, "문의");
    ChatMessage sellerMessage = messageRepository.append(room.roomId(), 10L, "답장");

    assertEquals(
        room.roomId(),
        roomRepository.findByListingIdAndInquirerId(100L, 20L).orElseThrow().roomId());
    assertEquals(
        List.of(sellerMessage.messageId(), buyerMessage.messageId()),
        messageRepository
            .findByRoomId(
                room.roomId(),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .stream()
            .map(ChatMessage::messageId)
            .toList());
    assertEquals(
        List.of(room.roomId()),
        messageRepository.findRoomIdsWithUnreadMessages(List.of(room.roomId()), 20L).stream()
            .toList());

    ChatReadPosition position =
        readPositionRepository.update(room.roomId(), 20L, sellerMessage.messageId());

    assertEquals(sellerMessage.messageId(), position.lastReadMessageId());
    assertTrue(
        messageRepository.findRoomIdsWithUnreadMessages(List.of(room.roomId()), 20L).isEmpty());
  }

  @Test
  void participantRoomPageUsesLatestActivityOrder() {
    ChatRoom olderRoom = roomRepository.create(100L, 10L, 20L);
    ChatRoom activeRoom = roomRepository.create(101L, 10L, 20L);
    messageRepository.append(olderRoom.roomId(), 10L, "최근 활동");

    List<Long> roomIds =
        roomRepository.findByParticipantId(20L, PageRequest.of(0, 20)).stream()
            .map(ChatRoom::roomId)
            .toList();

    assertEquals(List.of(olderRoom.roomId(), activeRoom.roomId()), roomIds);
  }
}
