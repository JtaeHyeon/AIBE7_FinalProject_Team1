package org.example.springtestci.trade.service;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.service.HistoryChangeReviewRecorder;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** RESERVED 판매글의 선택 구매 요청을 찾아 신규 history 사본을 P3 확인 대상으로 저장한다. */
@Component
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class ReservedHistoryChangeReviewRecorder implements HistoryChangeReviewRecorder {

  private final PublishedListingAccess listingAccess;
  private final PurchaseRequestRepository requestRepository;
  private final ReservationChangeService changes;

  public ReservedHistoryChangeReviewRecorder(
      PublishedListingAccess listingAccess,
      PurchaseRequestRepository requestRepository,
      ReservationChangeService changes) {
    this.listingAccess = listingAccess;
    this.requestRepository = requestRepository;
    this.changes = changes;
  }

  /**
   * 호출자가 잠근 제품에 연결된 RESERVED 판매글과 요청을 잠근 뒤 불변 사본을 저장한다.
   *
   * <p>RESERVED 판매글이 없으면 거래 부수효과 없이 종료한다. 판매글만 있고 선택 요청이 없으면 일관성 오류이므로 전체 history 쓰기를 롤백한다.
   */
  @Override
  public void recordCreated(
      long productId, long eventId, long factsVersion, JsonNode publicSafeSnapshot) {
    var listing = listingAccess.findReservedByProductIdForUpdate(productId);
    if (listing.isEmpty()) {
      return;
    }
    var request =
        requestRepository
            .findReservedByListingId(listing.get().listingId())
            .orElseThrow(() -> new CommonException(ErrorCode.TRADE_CONFLICT));
    changes.recordChange(productId, request.id(), eventId, factsVersion, publicSafeSnapshot);
  }
}
