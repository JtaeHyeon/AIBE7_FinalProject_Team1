package org.example.springtestci.chat.domain;

import java.time.Instant;

public record ChatReadPosition(
    long roomId, long userId, long lastReadMessageId, Instant updatedAt) {}
