package org.example.springtestci.trade.service;

import java.time.Instant;

/**
 * 거래 완료와 같은 DB 트랜잭션에서 소유권 이전·이전 이력·누적 이력 비공개 처리를 수행할 정식 외부 포트다.
 *
 * <p>운영 구현이 제공되지 않은 동안 성공 고정 구현이나 no-op Bean을 등록하지 않는다.
 */
public interface InternalTradeCompletionPort {

  void complete(Command command);

  record Command(
      long productId,
      long purchaseRequestId,
      long listingId,
      long fromUserId,
      long toUserId,
      long actorId,
      long confirmedFactsVersion,
      Instant completedAt) {}
}
