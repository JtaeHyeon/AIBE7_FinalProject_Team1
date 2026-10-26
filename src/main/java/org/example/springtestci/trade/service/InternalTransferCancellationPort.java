package org.example.springtestci.trade.service;

/**
 * P3 예약 취소 시 기존 INTERNAL/PENDING 소유권 이전을 취소하는 외부 포트다.
 *
 * <p>이 저장소에는 운영 구현이 없으며 테스트 대역은 운영 연결 완료의 근거가 아니다. 정식 ownership 구현은 정확히 한 건을 조건부 갱신하고 실패를 현재 트랜잭션에
 * 전파해야 한다.
 */
public interface InternalTransferCancellationPort {

  /** 같은 DataSource와 트랜잭션에서 정확히 일치하는 INTERNAL/PENDING 이전을 취소한다. */
  void cancelPending(Command command);

  record Command(long productId, long purchaseRequestId, long fromUserId, long toUserId) {}
}
