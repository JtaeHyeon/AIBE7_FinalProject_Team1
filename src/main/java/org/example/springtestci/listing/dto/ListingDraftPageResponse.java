package org.example.springtestci.listing.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ListingDraftPageResponse(List<ListingDraftSummaryResponse> items, PageMetadata page) {
  public ListingDraftPageResponse {
    items = List.copyOf(items);
  }

  public static ListingDraftPageResponse from(Page<ListingDraftSummaryResponse> result) {
    return new ListingDraftPageResponse(
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
