package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.listing.entity.ListingStatus;
import org.springframework.data.domain.Page;

/** 판매자가 게시했던 글의 목록이다. 종료된 글도 남기며 현재 제품 소유자의 정보는 포함하지 않는다. */
public record MyListingPageResponse(List<Item> items, ListingPageResponse.PageMetadata page) {
  public MyListingPageResponse {
    items = List.copyOf(items);
  }

  public static MyListingPageResponse from(Page<Item> result) {
    return new MyListingPageResponse(
        result.getContent(),
        new ListingPageResponse.PageMetadata(
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()));
  }

  public record Item(
      long listingId,
      String title,
      BigDecimal price,
      ListingStatus status,
      Instant publishedAt,
      String thumbnailUrl,
      boolean canManage) {}
}
