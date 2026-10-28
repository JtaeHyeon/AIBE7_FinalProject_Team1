package org.example.springtestci.listing.event;

/** 판매 조건 수정이 커밋되면 최신 버전으로 요약을 갱신한다. 이벤트에 판매자의 자유 입력은 담지 않는다. */
public record ListingContentChanged(long listingId) {}
