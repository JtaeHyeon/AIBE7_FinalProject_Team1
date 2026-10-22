package org.example.springtestci.listing.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ListingPageResponse(List<ListingSummaryResponse> items, PageMetadata page) {
  public ListingPageResponse {
    items = List.copyOf(items);
  }

  public static ListingPageResponse from(Page<ListingSummaryResponse> result) {
    return new ListingPageResponse(
        result.getContent(),
        new PageMetadata(
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()));
  }

  public record PageMetadata(
      int number, int size, long totalElements, int totalPages, boolean hasNext) {}
}
