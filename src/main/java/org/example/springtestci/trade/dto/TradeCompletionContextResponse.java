package org.example.springtestci.trade.dto;

import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;

/** 시리얼·증빙 등 관리용 필드를 제외한 공개 판매글 projection과 완료 판단 상태를 묶는다. */
public record TradeCompletionContextResponse(
    long tradeId,
    long roomId,
    long listingId,
    long productId,
    PurchaseRequestStatus requestStatus,
    TransactionStatus transactionStatus,
    PublishedListingStatus listingStatus,
    long factsVersion,
    Long sellerConfirmedVersion,
    Long buyerConfirmedVersion,
    boolean sellerConfirmedCurrentVersion,
    boolean buyerConfirmedCurrentVersion,
    long unreviewedChangeCount,
    boolean completionAllowed,
    ListingDetailResponse listing) {}
