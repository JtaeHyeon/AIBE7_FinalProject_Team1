package org.example.springtestci.chat.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.springtestci.chat.domain.ChatMessage;
import org.example.springtestci.chat.domain.ChatReadPosition;
import org.example.springtestci.chat.domain.ChatRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.features.chat-api.enabled=false")
@ActiveProfiles("local")
@Transactional
class ChatRepositoryIntegrationTest {

  @Autowired private ChatRoomRepository roomRepository;
  @Autowired private ChatMessageRepository messageRepository;
  @Autowired private ChatReadPositionRepository readPositionRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

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

    entityManager.flush();
    entityManager.clear();

    assertEquals(room.roomId(), roomRepository.findById(room.roomId()).orElseThrow().roomId());
    assertEquals(
        sellerMessage.messageId(),
        messageRepository.findById(sellerMessage.messageId()).orElseThrow().messageId());
    assertEquals(
        sellerMessage.messageId(),
        readPositionRepository
            .findByRoomIdAndUserId(room.roomId(), 20L)
            .orElseThrow()
            .lastReadMessageId());

    messageRepository.append(room.roomId(), 10L, "new reply");

    assertTrue(
        messageRepository
            .findRoomIdsWithUnreadMessages(List.of(room.roomId()), 20L)
            .contains(room.roomId()));
  }

  @Test
  void participantRoomPageUsesLatestActivityOrder() {
    ChatRoom olderRoom = roomRepository.create(100L, 10L, 20L);
    ChatRoom activeRoom = roomRepository.create(101L, 10L, 20L);
    ChatMessage latestMessage = messageRepository.append(olderRoom.roomId(), 10L, "최근 활동");
    jdbcTemplate.update(
        "update chat_messages set created_at = ? where id = ?",
        activeRoom.createdAt().plusSeconds(1),
        latestMessage.messageId());
    entityManager.clear();

    List<Long> roomIds =
        roomRepository.findByParticipantId(20L, PageRequest.of(0, 20)).stream()
            .map(ChatRoom::roomId)
            .toList();

    assertEquals(List.of(olderRoom.roomId(), activeRoom.roomId()), roomIds);
  }

  @Test
  void pagesMessagesWithoutDuplicatesOrGaps() {
    ChatRoom room = roomRepository.create(200L, 10L, 20L);
    ChatMessage first = messageRepository.append(room.roomId(), 20L, "first");
    ChatMessage second = messageRepository.append(room.roomId(), 10L, "second");
    ChatMessage third = messageRepository.append(room.roomId(), 20L, "third");
    Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    var firstPage = messageRepository.findByRoomId(room.roomId(), PageRequest.of(0, 2, sort));
    var nextPage = messageRepository.findByRoomId(room.roomId(), PageRequest.of(1, 2, sort));

    assertEquals(
        List.of(third.messageId(), second.messageId()), messageIds(firstPage.getContent()));
    assertEquals(List.of(first.messageId()), messageIds(nextPage.getContent()));
    assertTrue(firstPage.hasNext());
    assertFalse(nextPage.hasNext());
    assertEquals(3, firstPage.getTotalElements());
  }

  @Test
  void findsActualLatestMessageForEachRoom() {
    ChatRoom firstRoom = roomRepository.create(300L, 10L, 20L);
    ChatRoom secondRoom = roomRepository.create(301L, 10L, 20L);
    messageRepository.append(firstRoom.roomId(), 20L, "old");
    ChatMessage firstLatest = messageRepository.append(firstRoom.roomId(), 10L, "first latest");
    ChatMessage secondLatest = messageRepository.append(secondRoom.roomId(), 20L, "second latest");

    Map<Long, ChatMessage> latestByRoom =
        messageRepository
            .findLatestByRoomIds(List.of(firstRoom.roomId(), secondRoom.roomId()))
            .stream()
            .collect(Collectors.toMap(ChatMessage::roomId, Function.identity()));

    assertEquals(firstLatest.messageId(), latestByRoom.get(firstRoom.roomId()).messageId());
    assertEquals(secondLatest.messageId(), latestByRoom.get(secondRoom.roomId()).messageId());
  }

  private List<Long> messageIds(List<ChatMessage> messages) {
    return messages.stream().map(ChatMessage::messageId).toList();
  }

  @Test
  void filtersBothParticipantBranchesBeforePagingAndCountingBeyondFiftyRooms() {
    for (long inquirer = 100; inquirer < 155; inquirer++) {
      roomRepository.create(700L, 10L, inquirer);
    }
    roomRepository.create(701L, 10L, 20L);
    roomRepository.create(700L, 30L, 10L);
    roomRepository.create(700L, 30L, 40L);
    entityManager.flush();
    jdbcTemplate.update("update chat_rooms set created_at = TIMESTAMP '2026-10-01 00:00:00'");
    entityManager.clear();

    var first = roomRepository.findByParticipantIdAndListingId(10L, 700L, PageRequest.of(0, 20));
    var third = roomRepository.findByParticipantIdAndListingId(10L, 700L, PageRequest.of(2, 20));
    assertEquals(56, first.getTotalElements());
    assertEquals(3, first.getTotalPages());
    assertEquals(16, third.getNumberOfElements());
    assertTrue(first.hasNext());
    assertFalse(third.hasNext());
    assertTrue(first.stream().allMatch(room -> room.listingId() == 700L));
    List<Long> identifiers = first.stream().map(ChatRoom::roomId).toList();
    assertEquals(
        identifiers.stream().sorted(java.util.Comparator.reverseOrder()).toList(), identifiers);
    assertEquals(
        1,
        roomRepository
            .findByParticipantIdAndListingId(100L, 700L, PageRequest.of(0, 20))
            .getTotalElements());
    assertEquals(
        0,
        roomRepository
            .findByParticipantIdAndListingId(999L, 700L, PageRequest.of(0, 20))
            .getTotalElements());
    assertEquals(
        0,
        roomRepository
            .findByParticipantIdAndListingId(10L, 999L, PageRequest.of(0, 20))
            .getTotalElements());
  }
}
