package org.example.springtestci.chat.domain;

import java.util.Objects;

public class ChatAccessPolicy {

  public boolean canCreateRoom(ChatListingSnapshot listing, long actorId) {
    Objects.requireNonNull(listing, "listing must not be null");
    return listing.status() == ChatListingStatus.ACTIVE
        && listing.sellerId() == listing.currentOwnerId()
        && actorId != listing.sellerId();
  }

  public boolean canAccessRoom(ChatRoom room, long actorId) {
    Objects.requireNonNull(room, "room must not be null");
    return actorId == room.sellerId() || actorId == room.inquirerId();
  }
}
