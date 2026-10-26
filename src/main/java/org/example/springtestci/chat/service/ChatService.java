package org.example.springtestci.chat.service;

import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse;
import org.example.springtestci.chat.dto.ChatRoomResponse;
import org.example.springtestci.chat.dto.ChatRoomSummaryResponse;
import org.example.springtestci.chat.dto.ReadPositionResponse;

/** 인증 사용자의 채팅방·메시지·읽음 위치 유스케이스 계약이다. */
public interface ChatService {

  /** 판매글 구매 문의자용 방을 생성하거나 기존 방을 멱등하게 반환한다. */
  ChatRoomResponse openRoom(long listingId, long actorId);

  /** 사용자가 판매자 또는 문의자로 참여한 방만 페이지로 조회한다. */
  ChatPageResponse<ChatRoomSummaryResponse> listMyRooms(long actorId, int page, int size);

  /** 방 참여자에게만 메시지 내역을 반환한다. */
  ChatPageResponse<ChatMessageResponse> listMessages(long roomId, long actorId, int page, int size);

  /** 참여자 메시지를 저장하고 커밋 이후 실시간 전달을 예약한다. */
  ChatMessageResponse sendMessage(long roomId, long actorId, String content);

  /** 참여자가 실제 해당 방에 속한 메시지까지 읽었음을 기록한다. */
  ReadPositionResponse updateMyReadPosition(long roomId, long actorId, long lastReadMessageId);
}
