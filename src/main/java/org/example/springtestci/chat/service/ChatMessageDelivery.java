package org.example.springtestci.chat.service;

import org.example.springtestci.chat.dto.ChatMessageResponse;

/** 저장된 채팅 메시지를 트랜잭션 커밋 이후 외부 구독자에게 전달하는 포트다. */
public interface ChatMessageDelivery {

  /** 활성 트랜잭션의 커밋 콜백에 메시지 발행을 등록한다. */
  void publishAfterCommit(ChatMessageResponse message);
}
