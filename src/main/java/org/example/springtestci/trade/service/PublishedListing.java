package org.example.springtestci.trade.service;

/**
 * 거래 판단에 필요한 게시 판매글 사본이다.
 *
 * <p>게시 판매글 ID, 원본 제품 ID, 판매자 ID와 현재 제품 소유자 ID를 별도 값으로 유지해 연결 불일치를 검증한다.
 */
public record PublishedListing(
    long listingId,
    long productId,
    long sellerId,
    long currentOwnerId,
    PublishedListingStatus status) {}
