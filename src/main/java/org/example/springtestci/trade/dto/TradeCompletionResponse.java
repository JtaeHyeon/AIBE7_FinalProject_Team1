package org.example.springtestci.trade.dto;

import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

/** 완료 확인 저장 또는 실제 최종 완료 결과만 노출하는 최소 응답이다. */
public record TradeCompletionResponse(
    long tradeId,
    PurchaseRequestStatus requestStatus,
    TransactionStatus transactionStatus,
    PublishedListingStatus listingStatus,
    Long sellerConfirmedVersion,
    Long buyerConfirmedVersion,
    boolean completed) {}
