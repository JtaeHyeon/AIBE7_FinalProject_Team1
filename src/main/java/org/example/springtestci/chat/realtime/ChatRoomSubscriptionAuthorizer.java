package org.example.springtestci.chat.realtime;

import org.example.springtestci.chat.domain.ChatAccessPolicy;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.springframework.stereotype.Component;

@Component
class ChatRoomSubscriptionAuthorizer {

  private final ChatRoomRepository roomRepository;
  private final ChatAccessPolicy accessPolicy;

  ChatRoomSubscriptionAuthorizer(ChatRoomRepository roomRepository) {
    this.roomRepository = roomRepository;
    this.accessPolicy = new ChatAccessPolicy();
  }

  boolean canSubscribe(long roomId, long userId) {
    return roomRepository
        .findById(roomId)
        .map(room -> accessPolicy.canAccessRoom(room, userId))
        .orElse(false);
  }
}
