package org.example.springtestci.trade.service;

public interface InternalTransferPendingWriter {

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
