package org.example.springtestci.trade.service;

import java.time.Clock;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.dto.ChangeReviewPageResponse;
import org.example.springtestci.trade.dto.ChangeReviewPageResponse.PageMetadata;
import org.example.springtestci.trade.dto.ChangeReviewResponse;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.ReservationChangeReview;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.repository.ReservationChangeReviewRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
@Transactional(readOnly = true)
public class ReservationChangeService {

  private final PurchaseRequestRepository requestRepository;
  private final ReservationChangeReviewRepository reviewRepository;
  private final ChatRoomRepository roomRepository;
  private final PublishedListingAccess listingAccess;
  private final TradeCancellationService cancellationService;
  private final Clock clock;

  public ReservationChangeService(
      PurchaseRequestRepository requestRepository,
      ReservationChangeReviewRepository reviewRepository,
      ChatRoomRepository roomRepository,
      PublishedListingAccess listingAccess,
      TradeCancellationService cancellationService,
      Clock clock) {
    this.requestRepository = requestRepository;
    this.reviewRepository = reviewRepository;
    this.roomRepository = roomRepository;
    this.listingAccess = listingAccess;
    this.cancellationService = cancellationService;
    this.clock = clock;
  }

  @Transactional
  public ChangeReviewResponse recordChange(
      long productId,
      long purchaseRequestId,
      long eventId,
      long factsVersion,
      JsonNode changeFacts) {
    if (productId < 1 || purchaseRequestId < 1 || eventId < 1) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    PurchaseRequest initial = requireRequest(purchaseRequestId);
    PublishedListing listing = requireLockedListing(initial.listingId());
    PurchaseRequest request = requireLockedRequest(purchaseRequestId);
    requireReservedConnection(productId, request, listing);

    var existing = reviewRepository.find(purchaseRequestId, eventId);
    if (existing.isPresent()) {
      if (!existing.get().sameSnapshot(factsVersion, changeFacts)) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      return response(existing.get());
    }

    ReservationChangeReview review =
        ReservationChangeReview.pending(purchaseRequestId, eventId, factsVersion, changeFacts);
    if (!reviewRepository.saveIfAbsent(review)) {
      ReservationChangeReview concurrent = requireReview(purchaseRequestId, eventId);
      if (!concurrent.sameSnapshot(factsVersion, changeFacts)) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      return response(concurrent);
    }
    request.clearConfirmedVersions();
    requestRepository.clearConfirmedVersions(purchaseRequestId);
    return response(review);
  }

  public ChangeReviewPageResponse findAll(
      long purchaseRequestId, long actorId, int page, int size) {
    PurchaseRequest request = requireRequest(purchaseRequestId);
    requireParticipant(request.roomId(), actorId);
    var result = reviewRepository.findAll(purchaseRequestId, page, size);
    int totalPages =
        result.totalElements() == 0 ? 0 : (int) ((result.totalElements() + size - 1) / size);
    return new ChangeReviewPageResponse(
        result.items().stream().map(this::response).toList(),
        new PageMetadata(page, size, result.totalElements(), totalPages),
        reviewRepository.countUnreviewed(purchaseRequestId),
        request.transactionStatus());
  }

  @Transactional
  public ChangeReviewResponse review(
      long purchaseRequestId,
      long eventId,
      long actorId,
      ChangeReviewDecision decision,
      TradeCancellationReason reason) {
    validateDecision(decision, reason);
    PurchaseRequest initial = requireRequest(purchaseRequestId);
    ChatRoom room = requireParticipant(initial.roomId(), actorId);
    if (actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    ReservationChangeReview existing = requireReview(purchaseRequestId, eventId);
    if (existing.decision() != null) {
      existing.decide(decision, existing.reviewedAt());
      return response(existing);
    }
    if (!initial.isReserved()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    if (decision == ChangeReviewDecision.CANCEL) {
      cancellationService.cancelForChangeReview(purchaseRequestId, actorId, reason);
    } else {
      PublishedListing listing = requireLockedListing(initial.listingId());
      PurchaseRequest lockedRequest = requireLockedRequest(purchaseRequestId);
      requireReservedConnection(listing.productId(), lockedRequest, listing);
    }

    ReservationChangeReview locked =
        reviewRepository
            .findForUpdate(purchaseRequestId, eventId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (locked.decision() != null) {
      locked.decide(decision, locked.reviewedAt());
      return response(locked);
    }
    locked.decide(decision, clock.instant());
    reviewRepository.updateDecision(locked);
    return response(locked);
  }

  public boolean hasUnreviewedChanges(long purchaseRequestId) {
    return reviewRepository.countUnreviewed(purchaseRequestId) > 0;
  }

  public void requireNoUnreviewedChanges(long purchaseRequestId) {
    if (hasUnreviewedChanges(purchaseRequestId)) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private void validateDecision(ChangeReviewDecision decision, TradeCancellationReason reason) {
    if (decision == null
        || (decision == ChangeReviewDecision.CANCEL && reason == null)
        || (decision == ChangeReviewDecision.KEEP && reason != null)) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
  }

  private void requireReservedConnection(
      long productId, PurchaseRequest request, PublishedListing listing) {
    if (!request.isReserved()
        || request.listingId() != listing.listingId()
        || listing.productId() != productId
        || listing.currentOwnerId() != listing.sellerId()
        || listing.status() != PublishedListingStatus.RESERVED) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private PurchaseRequest requireRequest(long purchaseRequestId) {
    return requestRepository
        .findById(purchaseRequestId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private PurchaseRequest requireLockedRequest(long purchaseRequestId) {
    return requestRepository
        .findByIdForUpdate(purchaseRequestId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private PublishedListing requireLockedListing(long listingId) {
    return listingAccess
        .findByIdForUpdate(listingId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private ReservationChangeReview requireReview(long purchaseRequestId, long eventId) {
    return reviewRepository
        .find(purchaseRequestId, eventId)
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

  private ChangeReviewResponse response(ReservationChangeReview review) {
    return new ChangeReviewResponse(
        review.eventId(),
        review.factsVersion(),
        review.changeFacts(),
        review.decision(),
        review.reviewedAt());
  }
}
