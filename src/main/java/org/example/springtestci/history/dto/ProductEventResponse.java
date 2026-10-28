package org.example.springtestci.history.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;

public record ProductEventResponse(
    long eventId,
    long productId,
    long authorId,
    EventType eventType,
    LocalDate occurredOn,
    String title,
    String content,
    String serviceProvider,
    String replacedPart,
    EventVisibility visibility,
    EventStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<CorrectionResponse> corrections) {

  public static ProductEventResponse from(ProductEvent event) {
    List<CorrectionResponse> corrections =
        event.getCorrections() != null
            ? event.getCorrections().stream().map(CorrectionResponse::from).toList()
            : List.of();

    return new ProductEventResponse(
        event.getId(),
        event.getProductId(),
        event.getAuthorId(),
        event.getEventType(),
        event.getOccurredOn(),
        event.getTitle(),
        event.getContent(),
        event.getServiceProvider(),
        event.getReplacedPart(),
        event.getVisibility(),
        event.getStatus(),
        event.getCreatedAt(),
        event.getUpdatedAt(),
        corrections);
  }

  /** 공개 조회용: 작성자·정정자 회원 ID를 0으로 가린다. */
  public ProductEventResponse withoutActorIds() {
    return new ProductEventResponse(
        eventId,
        productId,
        0L,
        eventType,
        occurredOn,
        title,
        content,
        serviceProvider,
        replacedPart,
        visibility,
        status,
        createdAt,
        updatedAt,
        corrections.stream()
            .map(c -> new CorrectionResponse(c.correctionId(), 0L, c.reason(), c.createdAt()))
            .toList());
  }

  public record CorrectionResponse(
      long correctionId, long actorId, String reason, Instant createdAt) {
    public static CorrectionResponse from(
        org.example.springtestci.history.entity.ProductEventCorrection c) {
      long id = c.getId() != null ? c.getId() : 0L;
      return new CorrectionResponse(id, c.getActorId(), c.getReason(), c.getCreatedAt());
    }
  }
}
