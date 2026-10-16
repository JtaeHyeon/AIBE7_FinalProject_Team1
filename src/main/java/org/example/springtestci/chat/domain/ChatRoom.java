package org.example.springtestci.chat.domain;

import java.time.Instant;

public record ChatRoom(
    long roomId, long listingId, long sellerId, long inquirerId, Instant createdAt) {}
