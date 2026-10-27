package org.example.springtestci.listing.event;

/** 실제 최초 게시가 성공한 트랜잭션에서만 발행한다. 초안·사용자 원문을 이벤트에 담지 않는다. */
public record ListingPublished(long listingId) {}
