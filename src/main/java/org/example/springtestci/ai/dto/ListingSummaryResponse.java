package org.example.springtestci.ai.dto;

import java.time.Instant;
import org.example.springtestci.ai.entity.ListingSummaryState.Status;

/** 화면에 보낼 요약 상태·내용과 재시도 가능 여부다. 생성 시도 UUID·공급자 응답·내부 입력 자료는 제외한다. */
public record ListingSummaryResponse(
    Status status,
    String text,
    String errorCode,
    Instant updatedAt,
    boolean canRequest,
    long retryAfterSeconds) {}
