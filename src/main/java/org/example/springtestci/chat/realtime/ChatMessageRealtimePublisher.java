package org.example.springtestci.chat.realtime;

import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.service.ChatMessageDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 커밋된 채팅 메시지만 방별 STOMP 구독 경로로 발행하는 운영 어댑터다.
 *
 * <p>브로커 발행 실패는 이미 커밋된 DB 쓰기를 되돌릴 수 없으므로 식별자만 오류 로그에 남긴다.
 */
@Component
@ConditionalOnProperty(prefix = "app.features.chat-api", name = "enabled", havingValue = "true")
public class ChatMessageRealtimePublisher implements ChatMessageDelivery {

  private static final Logger log = LoggerFactory.getLogger(ChatMessageRealtimePublisher.class);
  private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/room/";

  private final SimpMessagingTemplate messagingTemplate;

  public ChatMessageRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  /** 활성 트랜잭션에 afterCommit 콜백을 등록하며 트랜잭션 밖 호출은 즉시 거부한다. */
  @Override
  public void publishAfterCommit(ChatMessageResponse message) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Chat message delivery requires an active transaction");
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publish(message);
          }
        });
  }

  private void publish(ChatMessageResponse message) {
    try {
      messagingTemplate.convertAndSend(ROOM_DESTINATION_PREFIX + message.roomId(), message);
    } catch (RuntimeException exception) {
      log.error(
          "Failed to publish committed chat message. roomId={}, messageId={}",
          message.roomId(),
          message.messageId(),
          exception);
    }
  }
}
