package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

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
