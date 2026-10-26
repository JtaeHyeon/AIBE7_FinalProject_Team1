package org.example.springtestci.trade.entity;

import java.time.Instant;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;

/**
 * P2 구매 요청과 예약 거래 상태를 함께 표현하는 도메인 모델이다.
 *
 * <p>PENDING 요청 취소는 거래 상태와 취소 메타데이터가 없고, RESERVED 취소만 거래 상태 CANCELLED와 최초 취소 메타데이터를 가진다.
 */
public class PurchaseRequest {

  private final Long id;
  private final long roomId;
  private final long listingId;
  private PurchaseRequestStatus requestStatus;
  private TransactionStatus transactionStatus;
  // 각 당사자가 거래 완료 전에 확인한 최신 제품 facts 버전이며 새 변경이 생기면 둘 다 NULL이 된다.
  private Long sellerConfirmedVersion;
  private Long buyerConfirmedVersion;
  // RESERVED 거래 취소에만 세 값이 함께 존재하고 수락 전 PENDING 취소에는 모두 NULL이다.
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

  /** 새 요청은 거래가 아직 시작되지 않아 거래 상태·확인 버전·취소 메타데이터가 모두 없다. */
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

  /** PENDING 요청을 ACCEPTED/RESERVED로 전환한다. */
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

  /** 예약 거래를 취소하고 양측 완료 확인 버전과 최초 취소 메타데이터를 함께 갱신한다. */
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

  /** 새 변경 사건 이후 완료 재확인을 요구하도록 판매자·구매자 확인 버전을 함께 제거한다. */
  public void clearConfirmedVersions() {
    sellerConfirmedVersion = null;
    buyerConfirmedVersion = null;
  }

  /** 판매자가 확인한 현재 제품 사실 버전을 기록한다. 예약 중 거래가 아니면 완료 확인을 허용하지 않는다. */
  public void confirmSeller(long factsVersion) {
    requireReserved();
    sellerConfirmedVersion = factsVersion;
  }

  /** 선택 구매자가 확인한 현재 제품 사실 버전을 기록한다. 예약 중 거래가 아니면 완료 확인을 허용하지 않는다. */
  public void confirmBuyer(long factsVersion) {
    requireReserved();
    buyerConfirmedVersion = factsVersion;
  }

  /** 양측이 같은 최신 버전을 확인한 예약만 COMPLETED로 전환한다. 요청 상태는 ACCEPTED를 유지한다. */
  public void complete(long factsVersion) {
    requireReserved();
    Long expectedVersion = factsVersion;
    if (!expectedVersion.equals(sellerConfirmedVersion)
        || !expectedVersion.equals(buyerConfirmedVersion)) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    transactionStatus = TransactionStatus.COMPLETED;
  }

  public boolean isReserved() {
    return requestStatus == PurchaseRequestStatus.ACCEPTED
        && transactionStatus == TransactionStatus.RESERVED;
  }

  public boolean isCompleted() {
    return requestStatus == PurchaseRequestStatus.ACCEPTED
        && transactionStatus == TransactionStatus.COMPLETED;
  }

  public boolean sellerConfirmedAt(long factsVersion) {
    return Long.valueOf(factsVersion).equals(sellerConfirmedVersion);
  }

  public boolean buyerConfirmedAt(long factsVersion) {
    return Long.valueOf(factsVersion).equals(buyerConfirmedVersion);
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

  private void requireReserved() {
    if (!isReserved()) {
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
