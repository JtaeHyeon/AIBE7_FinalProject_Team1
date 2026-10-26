package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

/**
 * RESERVED 거래 취소 후 구매 요청·거래·판매글 상태와 최초 취소 메타데이터를 반환한다.
 *
 * <p>{@code requestId}가 API의 tradeId이며 {@code roomId}, {@code listingId}와 서로 다른 식별자다.
 */
public record TradeCancellationResponse(
    long requestId,
    long roomId,
    long listingId,
    PurchaseRequestStatus requestStatus,
    TransactionStatus transactionStatus,
    PublishedListingStatus listingStatus,
    long cancelledById,
    TradeCancellationReason cancelReason,
    Instant cancelledAt) {}
