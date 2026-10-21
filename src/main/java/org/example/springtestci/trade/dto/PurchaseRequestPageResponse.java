package org.example.springtestci.trade.dto;

import java.util.List;
import org.example.springtestci.trade.service.PublishedListingStatus;

public record PurchaseRequestPageResponse(
    List<PurchaseRequestResponse> items, PageMetadata page, RoomContext roomContext) {

  public PurchaseRequestPageResponse {
    items = List.copyOf(items);
  }

  public record PageMetadata(int number, int size, long totalElements, int totalPages) {}

  public record RoomContext(long roomId, long listingId, PublishedListingStatus listingStatus) {}
}
