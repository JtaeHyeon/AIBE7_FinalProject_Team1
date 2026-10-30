package org.example.springtestci.trade.service;

/**
 * P2 예약 수락 시 INTERNAL/PENDING 소유권 이전 준비 건을 만드는 외부 포트다.
 *
 * <p>정식 ownership 모듈의 InternalTradeOwnershipService가 같은 DataSource·트랜잭션으로 구현한다. 기능 플래그가 꺼져 있으면 등록하지
 * 않는다.
 */
public interface InternalTransferPendingWriter {

  /**
   * 같은 DataSource와 Spring 트랜잭션에서 내부 소유권 이전 준비 건을 생성한다.
   *
   * <p>호출자는 검증된 제품·구매 요청·현재 소유자·수신자를 전달해야 한다. 구현은 구매 요청당 하나, 제품별 PENDING 최대 하나를 보장해야 하며 제품 소유자 변경이나
   * 이전 완료 처리는 하지 않는다. 충돌을 포함한 저장 실패는 숨기지 않고 호출자에게 전파해 거래 변경 전체가 롤백되게 한다.
   */
  void createPending(Command command);

  record Command(
      long productId,
      long purchaseRequestId,
      long fromUserId,
      long toUserId,
      String transferType,
      String status) {

    public static Command internalPending(
        long productId, long purchaseRequestId, long fromUserId, long toUserId) {
      return new Command(productId, purchaseRequestId, fromUserId, toUserId, "INTERNAL", "PENDING");
    }
  }
}
