package org.example.springtestci.history.service;

import java.util.List;
import java.util.Objects;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.dto.ChangeVisibilityRequest;
import org.example.springtestci.history.dto.CorrectEventRequest;
import org.example.springtestci.history.dto.CreateEventRequest;
import org.example.springtestci.history.dto.ProductEventResponse;
import org.example.springtestci.history.dto.VoidEventRequest;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.example.springtestci.history.repository.ProductEventRepository;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.product.repository.ProductRepository;
import org.example.springtestci.trade.repository.TradeAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductHistoryService {

  private final ProductEventRepository eventRepository;
  private final ProductRepository productRepository;
  private final TradeAuditRepository tradeAuditRepository;

  public ProductHistoryService(
      ProductEventRepository eventRepository,
      ProductRepository productRepository,
      TradeAuditRepository tradeAuditRepository) {
    this.eventRepository = eventRepository;
    this.productRepository = productRepository;
    this.tradeAuditRepository = tradeAuditRepository;
  }

  @Transactional
  public ProductEventResponse createEvent(long actorId, CreateEventRequest req) {
    Long ownerId =
        productRepository
            .findOwnerId(req.productId())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!Objects.equals(ownerId, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }

    ProductEvent event =
        ProductEvent.create(
            req.productId(),
            actorId,
            req.eventType(),
            req.occurredOn(),
            req.title(),
            req.content(),
            req.serviceProvider(),
            req.replacedPart(),
            req.visibility());

    ProductEvent saved = eventRepository.save(event);

    tradeAuditRepository.log(
        req.productId(),
        actorId,
        saved.getId(),
        "EVENT_CREATED",
        null,
        "type=" + req.eventType() + ", title=" + req.title(),
        "제품 이력 등록");

    return ProductEventResponse.from(saved);
  }

  public List<ProductEventResponse> getEvents(Long actorId, Long productId) {
    Long ownerId = productRepository.findOwnerId(productId).orElse(null);
    boolean isOwner = actorId != null && Objects.equals(ownerId, actorId);

    List<ProductEvent> events;
    if (isOwner) {
      events = eventRepository.findByProductIdOrderByOccurredOnDescIdDesc(productId);
    } else {
      events =
          eventRepository.findByProductIdAndVisibilityOrderByOccurredOnDescIdDesc(
              productId, EventVisibility.PUBLIC);
    }

    // 공개 조회자에게는 작성자 식별 정보를 내보내지 않는다.
    return events.stream()
        .map(ProductEventResponse::from)
        .map(response -> isOwner ? response : response.withoutActorIds())
        .toList();
  }

  /** 이전 소유자가 남긴 기록과 이미 무효 처리된 기록은 수정할 수 없다. */
  private static void requireEditable(ProductEvent event, long actorId) {
    if (!Objects.equals(event.getAuthorId(), actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    if (event.getStatus() == EventStatus.VOID) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
  }

  @Transactional
  public ProductEventResponse correctEvent(long actorId, Long eventId, CorrectEventRequest req) {
    ProductEvent event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    Long ownerId =
        productRepository
            .findOwnerId(event.getProductId())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!Objects.equals(ownerId, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }

    String beforeFacts = "title=" + event.getTitle() + ", content=" + event.getContent();
    requireEditable(event, actorId);
    event.correct(
        actorId,
        req.reason(),
        req.title(),
        req.content(),
        req.serviceProvider(),
        req.replacedPart());

    String afterFacts = "title=" + req.title() + ", content=" + req.content();

    tradeAuditRepository.log(
        event.getProductId(),
        actorId,
        event.getId(),
        "EVENT_CORRECTED",
        beforeFacts,
        afterFacts,
        req.reason());

    return ProductEventResponse.from(event);
  }

  /** 이력 공개 상태를 바꾼다. 현재 소유자이면서 작성자인 경우만 가능하며 변경은 감사 기록(SEC-08)에 남긴다. */
  @Transactional
  public ProductEventResponse changeVisibility(
      long actorId, Long eventId, ChangeVisibilityRequest req) {
    ProductEvent event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    Long ownerId =
        productRepository
            .findOwnerId(event.getProductId())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!Objects.equals(ownerId, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }

    requireEditable(event, actorId);
    EventVisibility before = event.getVisibility();
    if (before == req.visibility()) {
      return ProductEventResponse.from(event);
    }
    event.setVisibility(req.visibility());

    tradeAuditRepository.log(
        event.getProductId(),
        actorId,
        event.getId(),
        "EVENT_VISIBILITY_CHANGED",
        "visibility=" + before,
        "visibility=" + req.visibility(),
        "이력 공개 상태 변경");

    return ProductEventResponse.from(event);
  }

  @Transactional
  public ProductEventResponse voidEvent(long actorId, Long eventId, VoidEventRequest req) {
    ProductEvent event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    Long ownerId =
        productRepository
            .findOwnerId(event.getProductId())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!Objects.equals(ownerId, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }

    requireEditable(event, actorId);
    event.markVoid(actorId, req.reason());

    tradeAuditRepository.log(
        event.getProductId(),
        actorId,
        event.getId(),
        "EVENT_VOIDED",
        "status=VALID",
        "status=VOID",
        req.reason());

    return ProductEventResponse.from(event);
  }
}
