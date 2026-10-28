package org.example.springtestci.history.service;

import java.time.Instant;
import java.util.List;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.example.springtestci.history.repository.ProductEventRepository;
import org.example.springtestci.listing.service.ListingHistoryPrivatizer;
import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.example.springtestci.trade.repository.TradeAuditRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매글(Listing) 도메인과 제품 이력 도메인을 연결하는 어댑터. 테스트 환경(test 프로파일)에서는 테스트용 Fixture 빈이 우선하므로 !test 프로파일에서만
 * 활성화된다.
 */
@Component
@Profile("!test")
@Transactional(readOnly = true)
public class ProductHistoryListingAdapter
    implements ListingHistoryReader, ListingHistoryPublisher, ListingHistoryPrivatizer {

  private final ProductEventRepository eventRepository;
  private final TradeAuditRepository tradeAuditRepository;

  public ProductHistoryListingAdapter(
      ProductEventRepository eventRepository, TradeAuditRepository tradeAuditRepository) {
    this.eventRepository = eventRepository;
    this.tradeAuditRepository = tradeAuditRepository;
  }

  @Override
  public Snapshot read(long productId) {
    List<ProductEvent> events =
        eventRepository.findByProductIdOrderByOccurredOnDescIdDesc(productId);

    List<Event> listingEvents =
        events.stream()
            .map(
                e -> {
                  PublicText publicText =
                      new PublicText(
                          e.getTitle(),
                          e.getContent(),
                          e.getServiceProvider(),
                          e.getReplacedPart(),
                          e.isReviewed());

                  List<Correction> corrections =
                      e.getCorrections() != null
                          ? e.getCorrections().stream()
                              .map(
                                  c ->
                                      new Correction(
                                          c.getId(),
                                          c.getCreatedAt(),
                                          c.getReason(),
                                          c.isReviewed()))
                              .toList()
                          : List.of();

                  return new Event(
                      e.getId(),
                      e.getProductId(),
                      e.getAuthorId(),
                      e.getEventType(),
                      e.getOccurredOn(),
                      e.getCreatedAt(),
                      e.getStatus(),
                      publicText,
                      corrections);
                })
            .toList();

    return new Snapshot(State.READY, false, listingEvents);
  }

  @Override
  @Transactional
  public void publish(long productId, long listingId, long sellerId) {
    eventRepository.updateVisibilityByProductId(productId, EventVisibility.PUBLIC, Instant.now());

    tradeAuditRepository.log(
        productId,
        sellerId,
        listingId,
        "HISTORY_PUBLISHED",
        null,
        "visibility=PUBLIC",
        "판매글 게시에 따른 이력 공개 전환");
  }

  @Override
  @Transactional
  public void makePrivate(long productId, long listingId, long sellerId) {
    eventRepository.updateVisibilityByProductId(productId, EventVisibility.PRIVATE, Instant.now());

    tradeAuditRepository.log(
        productId,
        sellerId,
        listingId,
        "HISTORY_PRIVATIZED",
        "visibility=PUBLIC",
        "visibility=PRIVATE",
        "판매글 취소/종료에 따른 이력 비공개 전환");
  }
}
