package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.listing.entity.ListingDraft;

public record ListingDraftResponse(
    long draftId,
    long productId,
    String title,
    String description,
    BigDecimal price,
    Integer conditionGrade,
    List<Long> selectedImageIds,
    long version,
    Instant updatedAt) {

  public static ListingDraftResponse from(ListingDraft draft) {
    return new ListingDraftResponse(
        draft.getId(),
        draft.getProductId(),
        draft.getTitle(),
        draft.getDescription(),
        draft.getPrice(),
        draft.getConditionGrade(),
        draft.getSelectedImageIds(),
        draft.getVersion(),
        draft.getUpdatedAt());
  }
}
