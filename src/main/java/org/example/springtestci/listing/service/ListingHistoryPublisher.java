package org.example.springtestci.listing.service;

/**
 * 같은 DB·호출 트랜잭션에서 누적 이력 공개를 처리하는 이력 도메인 계약. 구현체는 REQUIRES_NEW·비동기·외부 HTTP로 분리하지 않고 실패 시 예외를 던져 게시도
 * 롤백해야 한다. 이력 작성·정정도 같은 제품 행 잠금을 사용해 게시 시점의 누적 이력이 빠지지 않게 한다.
 */
public interface ListingHistoryPublisher {
  void publish(long productId, long listingId, long sellerId);
}
