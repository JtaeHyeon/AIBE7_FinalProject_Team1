package org.example.springtestci.chat.domain;

public record ChatListingSnapshot(
    long listingId, long sellerId, long productId, long currentOwnerId, ChatListingStatus status) {}
