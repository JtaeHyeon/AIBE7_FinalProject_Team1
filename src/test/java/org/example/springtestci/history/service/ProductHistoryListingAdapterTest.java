package org.example.springtestci.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.example.springtestci.history.repository.ProductEventRepository;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;
import org.example.springtestci.listing.service.ListingHistoryReader.Snapshot;
import org.example.springtestci.listing.service.ListingHistoryReader.State;
import org.example.springtestci.trade.repository.TradeAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductHistoryListingAdapterTest {

  @Mock private ProductEventRepository eventRepository;
  @Mock private TradeAuditRepository tradeAuditRepository;

  private ProductHistoryListingAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ProductHistoryListingAdapter(eventRepository, tradeAuditRepository);
  }

  @Test
  @DisplayName("ListingHistoryReader 계약: 판매글용 누적 이력 스냅샷 조회")
  void listingHistoryReader_read() {
    long productId = 10L;
    ProductEvent event =
        ProductEvent.create(
            productId,
            1L,
            EventType.PURCHASE,
            LocalDate.of(2024, 1, 1),
            "신품 구매",
            "설명",
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(event, "id", 1L);

    given(eventRepository.findByProductIdOrderByOccurredOnDescIdDesc(productId))
        .willReturn(List.of(event));

    Snapshot snapshot = adapter.read(productId);
    assertThat(snapshot.state()).isEqualTo(State.READY);
    assertThat(snapshot.events()).hasSize(1);
    assertThat(snapshot.events().get(0).text().title()).isEqualTo("신품 구매");
  }

  @Test
  @DisplayName("ListingHistoryPublisher / Privatizer: 판매글 게시에 따른 공개 전환 및 비공개 전환")
  void publishAndPrivatize() {
    long productId = 10L;
    long listingId = 200L;
    long sellerId = 1L;

    adapter.publish(productId, listingId, sellerId);
    verify(eventRepository)
        .updateVisibilityByProductId(eq(productId), eq(EventVisibility.PUBLIC), any());
    verify(tradeAuditRepository)
        .log(
            eq(productId),
            eq(sellerId),
            eq(listingId),
            eq("HISTORY_PUBLISHED"),
            any(),
            any(),
            any());

    adapter.makePrivate(productId, listingId, sellerId);
    verify(eventRepository)
        .updateVisibilityByProductId(eq(productId), eq(EventVisibility.PRIVATE), any());
    verify(tradeAuditRepository)
        .log(
            eq(productId),
            eq(sellerId),
            eq(listingId),
            eq("HISTORY_PRIVATIZED"),
            any(),
            any(),
            any());
  }
}
