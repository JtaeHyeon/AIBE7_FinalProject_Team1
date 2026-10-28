package org.example.springtestci.trade.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.listing.service.ListingDetailService;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.example.springtestci.product.service.ProductService;
import org.example.springtestci.trade.dto.TradeCompletionContextResponse;
import org.example.springtestci.trade.dto.TradeCompletionResponse;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원래 거래 당사자의 최신 내용 확인을 저장하고 양측 확인이 모이면 거래 완료를 원자적으로 조정한다.
 *
 * <p>종료 재요청은 현재 소유권이나 제품 상태를 다시 검사하지 않는다. 진행 중 요청만 제품→판매글→판매글의 모든 구매 요청 순서로 잠근 뒤 버전·공개 이력·미확인 변경을
 * 검증한다.
 */
@Service
@ConditionalOnProperty(prefix = "app.features.trade-p4", name = "enabled", havingValue = "true")
@Transactional(readOnly = true)
public class TradeCompletionService {

  private final PurchaseRequestRepository requestRepository;
  private final ChatRoomRepository roomRepository;
  private final PublishedListingAccess listingAccess;
  private final ProductService productService;
  private final ListingDetailService listingDetailService;
  private final ReservationChangeService changeService;
  private final InternalTradeCompletionPort completionPort;
  private final TradeAuditRecorder auditRecorder;
  private final Clock clock;

  public TradeCompletionService(
      PurchaseRequestRepository requestRepository,
      ChatRoomRepository roomRepository,
      PublishedListingAccess listingAccess,
      ProductService productService,
      ListingDetailService listingDetailService,
      ReservationChangeService changeService,
      InternalTradeCompletionPort completionPort,
      TradeAuditRecorder auditRecorder,
      Clock clock) {
    this.requestRepository = requestRepository;
    this.roomRepository = roomRepository;
    this.listingAccess = listingAccess;
    this.productService = productService;
    this.listingDetailService = listingDetailService;
    this.changeService = changeService;
    this.completionPort = completionPort;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
  }

  /** 당사자와 예약 상태를 확인하고 같은 제품 잠금 안에서 공개 상세와 facts 버전을 조합한다. */
  @Transactional
  public TradeCompletionContextResponse getContext(
      long purchaseRequestId, long actorId, int page, int size) {
    PurchaseRequest initial = requireRequest(purchaseRequestId);
    ChatRoom room = requireParticipant(initial.roomId(), actorId);
    if (!initial.isReserved()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    PublishedListing listing = requireLockedListing(initial.listingId());
    PurchaseRequest locked = requireLockedTarget(listing.listingId(), purchaseRequestId);
    requireInProgressConnection(locked, room, listing);
    long factsVersion = currentFactsVersion(listing);
    ListingDetailResponse detail = requireOfficialDetail(listing, actorId, page, size);
    long unreviewedCount = changeService.countUnreviewedChanges(purchaseRequestId);
    return new TradeCompletionContextResponse(
        locked.id(),
        locked.roomId(),
        locked.listingId(),
        listing.productId(),
        locked.requestStatus(),
        locked.transactionStatus(),
        listing.status(),
        factsVersion,
        locked.sellerConfirmedVersion(),
        locked.buyerConfirmedVersion(),
        locked.sellerConfirmedAt(factsVersion),
        locked.buyerConfirmedAt(factsVersion),
        unreviewedCount,
        unreviewedCount == 0,
        detail);
  }

  /** 자신의 최신 확인만 저장하고, 마지막 확인이면 거래·판매글·다른 PENDING·소유권·감사를 한 트랜잭션에서 완료한다. */
  @Transactional
  public TradeCompletionResponse complete(
      long purchaseRequestId, long actorId, long confirmedFactsVersion) {
    if (confirmedFactsVersion < 1) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    PurchaseRequest initial = requireRequest(purchaseRequestId);
    ChatRoom room = requireParticipant(initial.roomId(), actorId);
    if (initial.isCompleted()) {
      return completedResponse(initial);
    }
    if (!initial.isReserved()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    PublishedListing listing = requireLockedListing(initial.listingId());
    PurchaseRequest locked = requireLockedTarget(listing.listingId(), purchaseRequestId);
    if (locked.isCompleted()) {
      return completedResponse(locked);
    }
    requireInProgressConnection(locked, room, listing);
    long currentFactsVersion = currentFactsVersion(listing);
    requireOfficialDetail(listing, actorId, 0, 1);
    changeService.requireNoUnreviewedChanges(purchaseRequestId);
    if (currentFactsVersion != confirmedFactsVersion) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    confirmActor(locked, room, actorId, confirmedFactsVersion);
    if (!locked.sellerConfirmedAt(currentFactsVersion)
        || !locked.buyerConfirmedAt(currentFactsVersion)) {
      return waitingResponse(locked);
    }

    // 상태 저장 이후 외부 이전과 감사까지 현재 Spring 트랜잭션에 참여해야 하며 어느 단계 실패든 전체 롤백한다.
    locked.complete(currentFactsVersion);
    requestRepository.complete(locked.id(), currentFactsVersion);
    listingAccess.sell(listing.listingId());
    requestRepository.rejectPendingByListingId(listing.listingId(), locked.id());
    Instant completedAt = clock.instant();
    completionPort.complete(
        new InternalTradeCompletionPort.Command(
            listing.productId(),
            locked.id(),
            listing.listingId(),
            listing.sellerId(),
            room.inquirerId(),
            actorId,
            currentFactsVersion,
            completedAt));
    auditRecorder.tradeCompleted(
        listing.productId(), actorId, locked.id(), currentFactsVersion, completedAt);
    return completedResponse(locked);
  }

  private void confirmActor(
      PurchaseRequest request, ChatRoom room, long actorId, long factsVersion) {
    if (actorId == room.sellerId()) {
      request.confirmSeller(factsVersion);
      requestRepository.confirmSeller(request.id(), factsVersion);
      return;
    }
    request.confirmBuyer(factsVersion);
    requestRepository.confirmBuyer(request.id(), factsVersion);
  }

  /** 판매글별 요청을 ID 순서로 모두 잠근 결과에서 현재 거래를 찾아 수락·완료 경합을 직렬화한다. */
  private PurchaseRequest requireLockedTarget(long listingId, long purchaseRequestId) {
    List<PurchaseRequest> requests = requestRepository.findByListingIdForUpdate(listingId);
    return requests.stream()
        .filter(request -> request.id() == purchaseRequestId)
        .findFirst()
        .orElseThrow(() -> new CommonException(ErrorCode.TRADE_CONFLICT));
  }

  private void requireInProgressConnection(
      PurchaseRequest request, ChatRoom room, PublishedListing listing) {
    if (!request.isReserved()
        || request.roomId() != room.roomId()
        || request.listingId() != room.listingId()
        || request.listingId() != listing.listingId()
        || room.sellerId() != listing.sellerId()
        || listing.currentOwnerId() != listing.sellerId()
        || listing.status() != PublishedListingStatus.RESERVED) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private long currentFactsVersion(PublishedListing listing) {
    return productService.getForListing(listing.sellerId(), listing.productId()).factsVersion();
  }

  /** 기존 공개 projection을 재사용하되 샘플 이력은 정식 완료 근거로 인정하지 않는다. */
  private ListingDetailResponse requireOfficialDetail(
      PublishedListing listing, long actorId, int page, int size) {
    ListingDetailResponse detail =
        listingDetailService.get(listing.listingId(), actorId, page, size);
    if (detail.history().status() != ListingHistoryReader.State.READY
        || detail.history().sample()) {
      throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
    }
    return detail;
  }

  private PurchaseRequest requireRequest(long purchaseRequestId) {
    return requestRepository
        .findById(purchaseRequestId)
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

  private PublishedListing requireLockedListing(long listingId) {
    return listingAccess
        .findByIdForUpdate(listingId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private TradeCompletionResponse waitingResponse(PurchaseRequest request) {
    return response(request, PublishedListingStatus.RESERVED, false);
  }

  private TradeCompletionResponse completedResponse(PurchaseRequest request) {
    if (!request.isCompleted()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    return response(request, PublishedListingStatus.SOLD, true);
  }

  private TradeCompletionResponse response(
      PurchaseRequest request, PublishedListingStatus listingStatus, boolean completed) {
    return new TradeCompletionResponse(
        request.id(),
        request.requestStatus(),
        request.transactionStatus(),
        listingStatus,
        request.sellerConfirmedVersion(),
        request.buyerConfirmedVersion(),
        completed);
  }
}
