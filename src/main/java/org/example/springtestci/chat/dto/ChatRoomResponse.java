package org.example.springtestci.chat.dto;

/** 생성·조회한 채팅방 ID와 그 방이 연결된 게시 판매글 ID를 구분해 반환한다. */
public record ChatRoomResponse(long roomId, long listingId) {}
