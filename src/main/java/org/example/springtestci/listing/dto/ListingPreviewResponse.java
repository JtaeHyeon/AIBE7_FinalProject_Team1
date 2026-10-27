package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 저장된 초안으로 구성한 읽기 전용 상세. 작성자 식별자·제품 별칭·증빙은 포함하지 않는다. */
public record ListingPreviewResponse(
    long draftId,
    long version,
    Instant savedAt,
    Content content,
    List<Image> images,
    ListingPublicationResponse.Product product,
    ListingPublicationResponse.History history) {
  public ListingPreviewResponse {
    images = List.copyOf(images);
  }

  public record Content(
      String title, String description, BigDecimal price, Integer conditionGrade) {}

  /** 첫 번째 사진이 대표 사진이며, 선택한 순서 그대로 제공한다. */
  public record Image(long imageId, String url) {}
}
