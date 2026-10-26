package org.example.springtestci.trade.entity;

import java.time.Instant;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;

public class PurchaseRequest {

  private final Long id;
  private final long roomId;
  private final long listingId;
  private PurchaseRequestStatus requestStatus;
  private TransactionStatus transactionStatus;
  private Long sellerConfirmedVersion;
  private Long buyerConfirmedVersion;
  private Long cancelledById;
  private TradeCancellationReason cancelReason;
  private Instant cancelledAt;

  private PurchaseRequest(
      Long id,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus,
      Long sellerConfirmedVersion,
      Long buyerConfirmedVersion,
      Long cancelledById,
      TradeCancellationReason cancelReason,
      Instant cancelledAt) {
    this.id = id;
    this.roomId = roomId;
    this.listingId = listingId;
    this.requestStatus = requestStatus;
    this.transactionStatus = transactionStatus;
    this.sellerConfirmedVersion = sellerConfirmedVersion;
    this.buyerConfirmedVersion = buyerConfirmedVersion;
    this.cancelledById = cancelledById;
    this.cancelReason = cancelReason;
    this.cancelledAt = cancelledAt;
  }

  public static PurchaseRequest pending(long roomId, long listingId) {
    return new PurchaseRequest(
        null, roomId, listingId, PurchaseRequestStatus.PENDING, null, null, null, null, null, null);
  }

  public static PurchaseRequest restore(
      long id,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus) {
    return restore(
        id, roomId, listingId, requestStatus, transactionStatus, null, null, null, null, null);
  }

  public static PurchaseRequest restore(
      long id,
      long roomId,
      long listingId,
      PurchaseRequestStatus requestStatus,
      TransactionStatus transactionStatus,
      Long sellerConfirmedVersion,
      Long buyerConfirmedVersion,
      Long cancelledById,
      TradeCancellationReason cancelReason,
      Instant cancelledAt) {
    return new PurchaseRequest(
        id,
        roomId,
        listingId,
        requestStatus,
        transactionStatus,
        sellerConfirmedVersion,
        buyerConfirmedVersion,
        cancelledById,
        cancelReason,
        cancelledAt);
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

  public void cancelReserved(
      long actorId, TradeCancellationReason reason, Instant cancellationTime) {
    if (requestStatus != PurchaseRequestStatus.ACCEPTED
        || transactionStatus != TransactionStatus.RESERVED) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    requestStatus = PurchaseRequestStatus.CANCELLED;
    transactionStatus = TransactionStatus.CANCELLED;
    sellerConfirmedVersion = null;
    buyerConfirmedVersion = null;
    cancelledById = actorId;
    cancelReason = reason;
    cancelledAt = cancellationTime;
  }

  public void clearConfirmedVersions() {
    sellerConfirmedVersion = null;
    buyerConfirmedVersion = null;
  }

  public boolean isReserved() {
    return requestStatus == PurchaseRequestStatus.ACCEPTED
        && transactionStatus == TransactionStatus.RESERVED;
  }

  public boolean isReservedCancellation() {
    return requestStatus == PurchaseRequestStatus.CANCELLED
        && transactionStatus == TransactionStatus.CANCELLED
        && cancelledById != null
        && cancelReason != null
        && cancelledAt != null;
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

  public Long sellerConfirmedVersion() {
    return sellerConfirmedVersion;
  }

  public Long buyerConfirmedVersion() {
    return buyerConfirmedVersion;
  }

  public Long cancelledById() {
    return cancelledById;
  }

  public TradeCancellationReason cancelReason() {
    return cancelReason;
  }

  public Instant cancelledAt() {
    return cancelledAt;
  }
}
