package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

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
