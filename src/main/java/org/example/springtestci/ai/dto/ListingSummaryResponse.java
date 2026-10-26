package org.example.springtestci.ai.dto;

import java.time.Instant;
import org.example.springtestci.ai.entity.ListingSummaryState.Status;

/** 공개 가능 상태만 반환한다. 생성 시도 UUID·공급자 응답·내부 입력 자료는 공개하지 않는다. */
public record ListingSummaryResponse(
    Status status,
    String text,
    String errorCode,
    Instant updatedAt,
    boolean canRequest,
    long retryAfterSeconds) {}
