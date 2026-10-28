package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import tools.jackson.databind.JsonNode;

/** 제품 이력 사건 ID, 당시 facts 버전·불변 공개 사본과 선택 구매자의 결정·시각을 반환한다. */
public record ChangeReviewResponse(
    long eventId,
    long factsVersion,
    JsonNode changeFacts,
    ChangeReviewDecision decision,
    Instant reviewedAt) {}
