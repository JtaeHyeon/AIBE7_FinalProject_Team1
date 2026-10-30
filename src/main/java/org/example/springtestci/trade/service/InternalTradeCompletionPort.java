package org.example.springtestci.trade.service;

import java.time.Instant;

/**
 * 거래 완료와 같은 DB 트랜잭션에서 소유권 이전·이전 이력·누적 이력 비공개 처리를 수행할 정식 외부 포트다.
 *
 * <p>정식 ownership 모듈의 InternalTradeOwnershipService가 구현한다. 실패는 상위 거래까지 롤백하며 성공 고정/no-op 구현으로 대체하지
 * 않는다.
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
