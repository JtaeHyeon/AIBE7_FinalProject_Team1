package org.example.springtestci.chat.domain;

import java.util.Objects;

/** 채팅방 생성과 기존 방 접근에 공통으로 쓰는 P1 권한 정책이다. */
public class ChatAccessPolicy {

  /** 활성 판매글의 현재 소유자가 판매자일 때 판매자 본인을 제외한 사용자만 문의할 수 있다. */
  public boolean canCreateRoom(ChatListingSnapshot listing, long actorId) {
    Objects.requireNonNull(listing, "listing must not be null");
    return listing.status() == ChatListingStatus.ACTIVE
        && listing.sellerId() == listing.currentOwnerId()
        && actorId != listing.sellerId();
  }

  /** 최초 방 판매자와 문의자만 해당 방에 접근할 수 있다. */
  public boolean canAccessRoom(ChatRoom room, long actorId) {
    Objects.requireNonNull(room, "room must not be null");
    return actorId == room.sellerId() || actorId == room.inquirerId();
  }
}
