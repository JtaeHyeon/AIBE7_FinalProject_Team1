package org.example.springtestci.chat.service;

import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse;
import org.example.springtestci.chat.dto.ChatRoomResponse;
import org.example.springtestci.chat.dto.ChatRoomSummaryResponse;
import org.example.springtestci.chat.dto.ReadPositionResponse;

public interface ChatService {

  ChatRoomResponse openRoom(long listingId, long actorId);

  ChatPageResponse<ChatRoomSummaryResponse> listMyRooms(long actorId, int page, int size);

  ChatPageResponse<ChatMessageResponse> listMessages(long roomId, long actorId, int page, int size);

  ChatMessageResponse sendMessage(long roomId, long actorId, String content);

  ReadPositionResponse updateMyReadPosition(long roomId, long actorId, long lastReadMessageId);
}
