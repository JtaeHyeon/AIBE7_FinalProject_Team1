package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 비회원에게도 제공하는 카드 정보. 본문·이력·계정·초안·증빙·파일 저장 경로는 포함하지 않는다. */
public record ListingSummaryResponse(
    long listingId,
    String title,
    BigDecimal price,
    int conditionGrade,
    Instant publishedAt,
    String manufacturer,
    String modelName,
    Specifications specifications,
    String thumbnailUrl) {

  public record Specifications(
      String cpu, String gpu, Integer ramGb, Integer storageGb, String storageType, String os) {}
}
