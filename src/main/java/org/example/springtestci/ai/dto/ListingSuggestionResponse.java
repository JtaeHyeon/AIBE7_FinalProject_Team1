package org.example.springtestci.ai.dto;

import java.time.Instant;

/** 제안 자체는 저장하지 않는다. sourceVersion은 생성 전후 자료의 동일성 검사 값이다. */
public record ListingSuggestionResponse(
    long productId,
    long factsVersion,
    String sourceVersion,
    String title,
    String description,
    Instant generatedAt,
    boolean sample) {}
