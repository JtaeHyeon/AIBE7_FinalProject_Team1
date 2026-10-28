package org.example.springtestci.chat.domain;

import java.time.Instant;

/**
 * 게시 판매글 하나에서 원래 판매자와 문의자를 연결한 채팅방 사본이다.
 *
 * <p>{@code roomId}는 채팅방, {@code listingId}는 게시 판매글 식별자이며 서로 대체할 수 없다.
 */
public record ChatRoom(
    long roomId, long listingId, long sellerId, long inquirerId, Instant createdAt) {}
