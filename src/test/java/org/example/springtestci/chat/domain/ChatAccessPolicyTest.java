package org.example.springtestci.chat.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChatAccessPolicyTest {

  private final ChatAccessPolicy policy = new ChatAccessPolicy();

  @Test
  void allowsAnotherUserToCreateRoomForActiveListingOwnedBySeller() {
    ChatListingSnapshot listing = listing(ChatListingStatus.ACTIVE, 10L, 10L);

    assertTrue(policy.canCreateRoom(listing, 20L));
  }

  @Test
  void rejectsSellerFromCreatingRoomForOwnListing() {
    ChatListingSnapshot listing = listing(ChatListingStatus.ACTIVE, 10L, 10L);

    assertFalse(policy.canCreateRoom(listing, 10L));
  }

  @Test
  void rejectsNewRoomWhenSellerNoLongerOwnsProduct() {
    ChatListingSnapshot listing = listing(ChatListingStatus.ACTIVE, 10L, 30L);

    assertFalse(policy.canCreateRoom(listing, 20L));
  }

  @ParameterizedTest
  @EnumSource(value = ChatListingStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
  void rejectsNewRoomForInactiveListing(ChatListingStatus status) {
    assertFalse(policy.canCreateRoom(listing(status, 10L, 10L), 20L));
  }

  @Test
  void allowsOnlyFixedParticipantsToAccessExistingRoom() {
    ChatRoom room = new ChatRoom(1L, 2L, 10L, 20L, Instant.EPOCH);

    assertTrue(policy.canAccessRoom(room, 10L));
    assertTrue(policy.canAccessRoom(room, 20L));
    assertFalse(policy.canAccessRoom(room, 30L));
  }

  private ChatListingSnapshot listing(
      ChatListingStatus status, long sellerId, long currentOwnerId) {
    return new ChatListingSnapshot(1L, sellerId, 2L, currentOwnerId, status);
  }
}
