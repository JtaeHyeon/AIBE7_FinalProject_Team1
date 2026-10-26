package org.example.springtestci.ai.entity;

import java.time.Instant;
import java.util.UUID;

/** listings의 요약 전용 열. JPA 판매글 변경이 요약 상태를 덮어쓰지 않도록 JDBC로 따로 관리한다. */
public record ListingSummaryState(
    Status status,
    String text,
    Long productVersion,
    Long listingVersion,
    UUID attemptId,
    String errorCode,
    Instant updatedAt) {
  public enum Status {
    NOT_REQUESTED,
    QUEUED,
    PROCESSING,
    READY,
    FAILED
  }
}
