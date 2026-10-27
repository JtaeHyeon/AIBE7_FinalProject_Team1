package org.example.springtestci.trade.entity;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;

public class PurchaseRequest {

  private final Long id;
  private final long roomId;
  private final long listingId;
  private PurchaseRequestStatus requestStatus;
  private TransactionStatus transactionStatus;

  private PurchaseRequest(
      Long id,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus) {
    this.id = id;
    this.roomId = roomId;
    this.listingId = listingId;
    this.requestStatus = requestStatus;
    this.transactionStatus = transactionStatus;
  }

  public static PurchaseRequest pending(long roomId, long listingId) {
    return new PurchaseRequest(null, roomId, listingId, PurchaseRequestStatus.PENDING, null);
  }

  public static PurchaseRequest restore(
      long id,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus) {
    return new PurchaseRequest(id, roomId, listingId, requestStatus, transactionStatus);
  }

  public void accept() {
    requirePending();
    requestStatus = PurchaseRequestStatus.ACCEPTED;
    transactionStatus = TransactionStatus.RESERVED;
  }

  public void reject() {
    requirePending();
    requestStatus = PurchaseRequestStatus.REJECTED;
  }

  public void cancelPending() {
    requirePending();
    requestStatus = PurchaseRequestStatus.CANCELLED;
  }

  private void requirePending() {
    if (requestStatus != PurchaseRequestStatus.PENDING || transactionStatus != null) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  public Long id() {
    return id;
  }

  public long roomId() {
    return roomId;
  }

  public long listingId() {
    return listingId;
  }

  public PurchaseRequestStatus requestStatus() {
    return requestStatus;
  }

  public TransactionStatus transactionStatus() {
    return transactionStatus;
  }
}
