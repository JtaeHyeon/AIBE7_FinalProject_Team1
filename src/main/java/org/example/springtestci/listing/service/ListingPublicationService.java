package org.example.springtestci.listing.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse.Correction;
import org.example.springtestci.listing.dto.ListingPublicationResponse.DetailsStatus;
import org.example.springtestci.listing.dto.ListingPublicationResponse.History;
import org.example.springtestci.listing.dto.ListingPublicationResponse.Page;
import org.example.springtestci.listing.dto.ListingPublicationResponse.Product;
import org.example.springtestci.listing.service.ListingHistoryReader.Event;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;
import org.example.springtestci.listing.service.ListingHistoryReader.Snapshot;
import org.example.springtestci.listing.service.ListingHistoryReader.State;
import org.example.springtestci.product.service.ProductService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 제품의 공개 허용 필드와 누적 이력 표시를 구성한다. 이력의 공개 상태는 변경하지 않는다. */
@Service
public class ListingPublicationService {
  private static final int MAX_SOURCE_EVENTS = 10_000;
  private static final int MAX_CORRECTIONS = 100;
  private static final Comparator<Event> TIMELINE_ORDER =
      Comparator.comparing(Event::occurredOn)
          .thenComparing(Event::createdAt)
          .thenComparingLong(Event::eventId)
          .reversed();
  // 보조 방어다. 사람 이름·주소 등 모든 개인정보를 판별하지 못하므로 원천의 공개 검토를 대체하지 않는다.
  private static final Pattern SENSITIVE_TEXT =
      Pattern.compile(
          "(?i)(?:https?://|www\\.|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|"
              + "(?:0\\d{1,2})[- .]?\\d{3,4}[- .]?\\d{4}|\\d{6}[- ]?[1-4]\\d{6}|"
              + "(?:\\d[\\d,]*\\s*원)|시리얼|serial|증빙|영수증|보증서|계좌|연락처|주소|주문번호)");

  private final ProductService products;
  private final ObjectProvider<ListingHistoryReader> historyReaders;

  public ListingPublicationService(
      ProductService products, ObjectProvider<ListingHistoryReader> historyReaders) {
    this.products = products;
    this.historyReaders = historyReaders;
  }

  /** 조회 도중 소유권 변경이 끼어들지 않도록 기존 제품 잠금 안에서 DTO를 완성한다. */
  @Transactional
  public ListingPublicationResponse get(long productId, long userId, int page, int size) {
    return compose(productId, userId, page, size);
  }

  /** AI에는 화면의 첫 페이지만이 아닌 전체 누적 핵심 이력을 제공한다. 외부 호출은 이 트랜잭션 밖에서 한다. */
  @Transactional
  public ListingPublicationResponse getForAi(long productId, long userId) {
    return compose(productId, userId, 0, MAX_SOURCE_EVENTS);
  }

  private ListingPublicationResponse compose(long productId, long userId, int page, int size) {
    products.lockOwnedForListing(userId, productId);
    var source = products.getForListing(userId, productId);
    var safeProduct = DraftProductResponse.from(source);
    var product =
        new Product(
            productId,
            safeProduct.manufacturer(),
            safeProduct.modelName(),
            safeProduct.purchaseDate(),
            safeProduct.warrantyExpiresAt(),
            safeProduct.specifications());
    ListingHistoryReader reader = historyReaders.getIfAvailable();
    Snapshot snapshot = reader == null ? Snapshot.unavailable() : reader.read(productId);
    validate(snapshot, productId);
    if (snapshot.state() == State.UNAVAILABLE) {
      return new ListingPublicationResponse(
          product,
          new History(
              State.UNAVAILABLE, false, List.of(), new Page(page, size, 0, 0, false), 0, 0));
    }
    var sorted = snapshot.events().stream().sorted(TIMELINE_ORDER).toList();
    int start = (int) Math.min((long) page * size, sorted.size());
    int end = Math.min(start + size, sorted.size());
    var items = sorted.subList(start, end).stream().map(event -> project(event, userId)).toList();
    long valid = sorted.stream().filter(event -> event.status() == EventStatus.VALID).count();
    return new ListingPublicationResponse(
        product,
        new History(
            State.READY,
            snapshot.sample(),
            items,
            new Page(
                page, size, sorted.size(), (sorted.size() + size - 1) / size, end < sorted.size()),
            valid,
            sorted.size() - valid));
  }

  /** 다른 제품의 행이나 불완전한 원천 데이터는 노출하지 않고 연동 오류로 처리한다. */
  private void validate(Snapshot snapshot, long productId) {
    if (snapshot == null
        || snapshot.state() == null
        || snapshot.events().size() > MAX_SOURCE_EVENTS
        || (snapshot.state() == State.UNAVAILABLE && !snapshot.events().isEmpty())) unavailable();
    var ids = new HashSet<Long>();
    for (Event event : snapshot.events()) {
      if (event.eventId() <= 0
          || event.productId() != productId
          || !ids.add(event.eventId())
          || event.eventType() == null
          || event.status() == null
          || event.occurredOn() == null
          || event.createdAt() == null
          || event.corrections().size() > MAX_CORRECTIONS) unavailable();
      if (event.eventType() == EventType.TRANSFER
          ? event.authorId() != null
          : event.authorId() == null || event.authorId() <= 0) unavailable();
      var correctionIds = new HashSet<Long>();
      for (var correction : event.corrections()) {
        if (correction.correctionId() <= 0
            || correction.correctedAt() == null
            || !correctionIds.add(correction.correctionId())) unavailable();
      }
    }
  }

  /** 미검토 텍스트와 시스템 이전의 자유 입력은 제외하되 기록의 존재·상태는 유지한다. */
  private ListingPublicationResponse.Event project(Event event, long userId) {
    var text = event.text();
    boolean reviewed =
        event.eventType() != EventType.TRANSFER
            && text != null
            && text.reviewed()
            && safe(text.title())
            && safe(text.content())
            && safe(text.serviceProvider())
            && safe(text.replacedPart());
    boolean system = event.eventType() == EventType.TRANSFER;
    var corrections =
        event.corrections().stream()
            .sorted(
                Comparator.comparing(ListingHistoryReader.Correction::correctedAt)
                    .thenComparingLong(ListingHistoryReader.Correction::correctionId)
                    .reversed())
            .map(
                change -> {
                  boolean approved =
                      change.reviewed()
                          && change.reason() != null
                          && !change.reason().isBlank()
                          && safe(change.reason())
                          && !system;
                  return new Correction(
                      change.correctionId(),
                      change.correctedAt(),
                      approved ? change.reason() : null,
                      approved ? DetailsStatus.REVIEWED : DetailsStatus.REVIEW_PENDING);
                })
            .toList();
    return new ListingPublicationResponse.Event(
        event.eventId(),
        event.eventType(),
        event.occurredOn(),
        event.createdAt(),
        event.status(),
        reviewed && text.title() != null && !text.title().isBlank()
            ? text.title()
            : label(event.eventType()),
        reviewed ? text.content() : null,
        reviewed ? text.serviceProvider() : null,
        reviewed ? text.replacedPart() : null,
        !system && event.authorId() != null && event.authorId() != userId,
        system,
        reviewed || system ? DetailsStatus.REVIEWED : DetailsStatus.REVIEW_PENDING,
        corrections);
  }

  private boolean safe(String value) {
    return value == null || (value.length() <= 2000 && !SENSITIVE_TEXT.matcher(value).find());
  }

  private String label(EventType type) {
    return switch (type) {
      case PURCHASE -> "제품 구매";
      case INSPECTION -> "제품 점검";
      case REPAIR -> "제품 수리";
      case PART_REPLACEMENT -> "부품 교체";
      case SPEC_CHANGE -> "사양 변경";
      case TRANSFER -> "소유권 이전 완료";
    };
  }

  private void unavailable() {
    throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
  }
}
