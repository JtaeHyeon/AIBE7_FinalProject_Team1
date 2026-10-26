package org.example.springtestci.trade.service;

import java.time.Clock;
import java.time.Instant;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.dto.TradeCancellationResponse;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
@Transactional(readOnly = true)
/**
 * P3 RESERVED 거래 취소의 공통 원자 처리 서비스다.
 *
 * <p>원래 판매자 또는 선택 구매자만 취소할 수 있다. INTERNAL/PENDING 이전, 구매 요청, 판매글 예약과 감사 기록을 같은 트랜잭션에서 변경하며 어느 단계든
 * 실패하면 전체를 롤백한다.
 */
public class TradeCancellationService {

  private final PurchaseRequestRepository requestRepository;
  private final ChatRoomRepository roomRepository;
  private final PublishedListingAccess listingAccess;
  private final InternalTransferCancellationPort transferCancellation;
  private final TradeAuditRecorder auditRecorder;
  private final Clock clock;

  public TradeCancellationService(
      PurchaseRequestRepository requestRepository,
      ChatRoomRepository roomRepository,
      PublishedListingAccess listingAccess,
      InternalTransferCancellationPort transferCancellation,
      TradeAuditRecorder auditRecorder,
      Clock clock) {
    this.requestRepository = requestRepository;
    this.roomRepository = roomRepository;
    this.listingAccess = listingAccess;
    this.transferCancellation = transferCancellation;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
  }

  @Transactional
  /** 예약 취소 API용 진입점으로, 이미 끝난 동일 예약 취소는 최초 기록을 보존해 멱등 반환한다. */
  public TradeCancellationResponse cancel(
      long purchaseRequestId, long actorId, TradeCancellationReason reason) {
    return cancel(purchaseRequestId, actorId, reason, true);
  }

  /** 변경 review가 공통 잠금을 확보한 뒤 호출하는 내부 경로다. */
  void cancelForChangeReview(
      PurchaseRequest request,
      ChatRoom room,
      PublishedListing listing,
      long actorId,
      TradeCancellationReason reason) {
    cancelLocked(request, room, listing, actorId, reason, true);
  }

  private TradeCancellationResponse cancel(
      long purchaseRequestId,
      long actorId,
      TradeCancellationReason reason,
      boolean allowAlreadyCancelled) {
    if (reason == null) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    PurchaseRequest initial = requireRequest(purchaseRequestId);
    ChatRoom room = requireParticipant(initial.roomId(), actorId);
    requireOriginalParties(room, actorId);
    if (initial.isReservedCancellation()) {
      if (!allowAlreadyCancelled) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      return response(initial, currentListingStatus(initial.listingId()));
    }
    if (!initial.isReserved()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    PublishedListing listing = requireLockedListing(initial.listingId());
    PurchaseRequest locked = requireLockedRequest(purchaseRequestId);
    if (locked.isReservedCancellation()) {
      if (!allowAlreadyCancelled) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      return response(locked, listing.status());
    }
    cancelLocked(locked, room, listing, actorId, reason, true);
    return response(locked, PublishedListingStatus.ACTIVE);
  }

  /** R07 판매 중지 트랜잭션이 이미 CANCELLED로 바꾼 판매글을 복구하지 않고 거래만 정리한다. */
  void cancelForListingShutdown(
      PurchaseRequest request,
      ChatRoom room,
      PublishedListing cancelledListing,
      long actorId,
      TradeCancellationReason reason) {
    cancelLocked(request, room, cancelledListing, actorId, reason, false);
  }

  private void cancelLocked(
      PurchaseRequest request,
      ChatRoom room,
      PublishedListing listing,
      long actorId,
      TradeCancellationReason reason,
      boolean releaseListing) {
    requireOriginalParties(room, actorId);
    if (request.listingId() != listing.listingId()
        || request.roomId() != room.roomId()
        || room.listingId() != listing.listingId()
        || room.sellerId() != listing.sellerId()
        || listing.currentOwnerId() != listing.sellerId()
        || !request.isReserved()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    PublishedListingStatus expectedStatus =
        releaseListing ? PublishedListingStatus.RESERVED : PublishedListingStatus.CANCELLED;
    if (listing.status() != expectedStatus) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    // 외부 이전 취소부터 감사까지 모두 현재 Spring 트랜잭션에 참여해야 한다.
    transferCancellation.cancelPending(
        new InternalTransferCancellationPort.Command(
            listing.productId(), request.id(), listing.sellerId(), room.inquirerId()));
    Instant cancelledAt = clock.instant();
    request.cancelReserved(actorId, reason, cancelledAt);
    requestRepository.cancelReserved(request);
    if (releaseListing) {
      listingAccess.releaseReservation(listing.listingId());
    }
    auditRecorder.reservationCancelled(
        listing.productId(), actorId, request.id(), reason, cancelledAt);
  }

  private PurchaseRequest requireRequest(long requestId) {
    return requestRepository
        .findById(requestId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private PurchaseRequest requireLockedRequest(long requestId) {
    return requestRepository
        .findByIdForUpdate(requestId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private PublishedListing requireLockedListing(long listingId) {
    return listingAccess
        .findByIdForUpdate(listingId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private ChatRoom requireParticipant(long roomId, long actorId) {
    ChatRoom room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (actorId != room.sellerId() && actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return room;
  }

  private void requireOriginalParties(ChatRoom room, long actorId) {
    if (actorId != room.sellerId() && actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
  }

  private PublishedListingStatus currentListingStatus(long listingId) {
    return listingAccess
        .findById(listingId)
        .map(PublishedListing::status)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private TradeCancellationResponse response(
      PurchaseRequest request, PublishedListingStatus listingStatus) {
    if (!request.isReservedCancellation()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    return new TradeCancellationResponse(
        request.id(),
        request.roomId(),
        request.listingId(),
        request.requestStatus(),
        request.transactionStatus(),
        listingStatus,
        request.cancelledById(),
        request.cancelReason(),
        request.cancelledAt());
  }
}
