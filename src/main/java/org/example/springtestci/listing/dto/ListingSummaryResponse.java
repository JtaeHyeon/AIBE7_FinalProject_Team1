package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 카드 기본 정보는 공개하며, 이력을 사용한 AI 소개는 인증된 회원에게만 포함한다. */
public record ListingSummaryResponse(
    long listingId,
    String title,
    BigDecimal price,
    int conditionGrade,
    Instant publishedAt,
    String manufacturer,
    String modelName,
    Specifications specifications,
    String thumbnailUrl,
    AiSummary aiSummary) {

  /** 생성 이력이나 내부 버전·오류 원문 대신 카드 표시용 상태와 짧은 문구만 제공한다. */
  public record AiSummary(String status, String text, boolean outdated) {}

  public ListingSummaryResponse withAiSummary(AiSummary value) {
    return new ListingSummaryResponse(
        listingId,
        title,
        price,
        conditionGrade,
        publishedAt,
        manufacturer,
        modelName,
        specifications,
        thumbnailUrl,
        value);
  }

  public record Specifications(
      String cpu, String gpu, Integer ramGb, Integer storageGb, String storageType, String os) {}
}
