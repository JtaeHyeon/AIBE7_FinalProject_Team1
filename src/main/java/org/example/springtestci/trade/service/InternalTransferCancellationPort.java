package org.example.springtestci.trade.service;

public interface InternalTransferCancellationPort {

  /** 같은 DataSource와 트랜잭션에서 정확히 일치하는 INTERNAL/PENDING 이전을 취소한다. */
  void cancelPending(Command command);

  record Command(long productId, long purchaseRequestId, long fromUserId, long toUserId) {}
}
