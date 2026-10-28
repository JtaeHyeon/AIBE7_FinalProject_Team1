package org.example.springtestci.trade.dto;

import java.util.List;
import org.example.springtestci.trade.service.PublishedListingStatus;

/** 구매 요청 목록과 페이지 정보, 선택 방 필터의 방·판매글 문맥을 반환한다. */
public record PurchaseRequestPageResponse(
    List<PurchaseRequestResponse> items, PageMetadata page, RoomContext roomContext) {

  public PurchaseRequestPageResponse {
    items = List.copyOf(items);
  }

  public record PageMetadata(int number, int size, long totalElements, int totalPages) {}

  /** 방 ID와 그 방이 연결된 게시 판매글 ID·현재 상태를 함께 제공한다. */
  public record RoomContext(long roomId, long listingId, PublishedListingStatus listingStatus) {}
}
