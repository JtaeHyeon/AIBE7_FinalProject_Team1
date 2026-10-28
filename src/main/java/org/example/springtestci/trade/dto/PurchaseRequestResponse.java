package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

/**
 * 구매 요청 ID, 채팅방 ID와 게시 판매글 ID를 구분해 제공하는 P2/P3 조회 응답이다.
 *
 * <p>수락 전 요청의 {@code transactionStatus}는 {@code null}이다. 취소 수행자·사유·시각은 RESERVED 거래 취소에만 존재하고
 * PENDING 요청 취소에는 모두 {@code null}이다.
 */
public record PurchaseRequestResponse(
    long requestId,
    long roomId,
    long listingId,
    PurchaseRequestStatus requestStatus,
    TransactionStatus transactionStatus,
    PublishedListingStatus listingStatus,
    Long cancelledById,
    TradeCancellationReason cancelReason,
    Instant cancelledAt) {

  public PurchaseRequestResponse(
      long requestId,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus,
      PublishedListingStatus listingStatus) {
    this(
        requestId,
        roomId,
        listingId,
        requestStatus,
        transactionStatus,
        listingStatus,
        null,
        null,
        null);
  }
}
