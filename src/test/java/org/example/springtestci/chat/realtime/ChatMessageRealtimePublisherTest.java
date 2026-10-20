package org.example.springtestci.chat.realtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ChatMessageRealtimePublisherTest {

  @Test
  void publishesMessageOnlyAfterCommit() {
    SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
    ChatMessageRealtimePublisher publisher = new ChatMessageRealtimePublisher(messagingTemplate);
    ChatMessageResponse response =
        new ChatMessageResponse(70L, 55L, 101L, "본문", Instant.parse("2026-10-19T00:00:00Z"));

    TransactionSynchronizationManager.initSynchronization();
    try {
      publisher.publishAfterCommit(response);
      verify(messagingTemplate, org.mockito.Mockito.never())
          .convertAndSend("/sub/chat/room/55", response);
      TransactionSynchronizationManager.getSynchronizations().stream()
          .forEach(TransactionSynchronization::afterCommit);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(messagingTemplate).convertAndSend("/sub/chat/room/55", response);
  }

  @Test
  void doesNotPublishRolledBackMessage() {
    SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
    ChatMessageRealtimePublisher publisher = new ChatMessageRealtimePublisher(messagingTemplate);
    ChatMessageResponse response =
        new ChatMessageResponse(70L, 55L, 101L, "본문", Instant.parse("2026-10-19T00:00:00Z"));

    TransactionSynchronizationManager.initSynchronization();
    try {
      publisher.publishAfterCommit(response);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(messagingTemplate, org.mockito.Mockito.never())
        .convertAndSend("/sub/chat/room/55", response);
  }

  @Test
  void rejectsDeliveryOutsideTransaction() {
    SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
    ChatMessageRealtimePublisher publisher = new ChatMessageRealtimePublisher(messagingTemplate);
    ChatMessageResponse response =
        new ChatMessageResponse(70L, 55L, 101L, "본문", Instant.parse("2026-10-19T00:00:00Z"));

    assertThrows(IllegalStateException.class, () -> publisher.publishAfterCommit(response));
  }
}
