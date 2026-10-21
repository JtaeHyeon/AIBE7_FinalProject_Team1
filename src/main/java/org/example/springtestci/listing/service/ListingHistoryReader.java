package org.example.springtestci.listing.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 이력 기능이 제공할 판매용 읽기 계약. 호출자는 제품 행 잠금과 현재 소유권 검사를 마친다. 구현체는 같은 제품의 누적 핵심 이력 전체를 반환하고, 개인정보를 제거·검토한
 * 텍스트만 reviewed=true로 표시한다. 비용·시리얼·문서·계정 정보 및 감사 로그 원문은 이 계약에 포함하지 않는다.
 */
public interface ListingHistoryReader {

  /** 연동 준비 중과 실제 이력 없음(READY + 빈 목록)을 구분한다. 최대 10,000건을 제공한다. */
  Snapshot read(long productId);

  record Snapshot(State state, boolean sample, List<Event> events) {
    public Snapshot {
      events = List.copyOf(events);
    }

    public static Snapshot unavailable() {
      return new Snapshot(State.UNAVAILABLE, false, List.of());
    }
  }

  enum State {
    READY,
    UNAVAILABLE
  }

  enum EventType {
    PURCHASE,
    INSPECTION,
    REPAIR,
    PART_REPLACEMENT,
    SPEC_CHANGE,
    TRANSFER
  }

  enum EventStatus {
    VALID,
    VOID
  }

  /** authorId는 이전 소유자 여부 계산에만 사용하며 판매용 응답으로 내보내지 않는다. */
  record Event(
      long eventId,
      long productId,
      Long authorId,
      EventType eventType,
      LocalDate occurredOn,
      Instant createdAt,
      EventStatus status,
      PublicText text,
      List<Correction> corrections) {
    public Event {
      corrections = List.copyOf(corrections);
    }
  }

  /** reviewed는 공개용 텍스트에 대한 개인정보 검토 완료 여부다. 미검토 텍스트는 출력하지 않는다. */
  record PublicText(
      String title,
      String content,
      String serviceProvider,
      String replacedPart,
      boolean reviewed) {}

  /** 사유도 별도로 검토한다. 변경 전후 원문·처리자 계정·증빙 정보는 전달하지 않는다. */
  record Correction(long correctionId, Instant correctedAt, String reason, boolean reviewed) {}
}
