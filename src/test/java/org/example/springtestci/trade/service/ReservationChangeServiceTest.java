package org.example.springtestci.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.ReservationChangeReview;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.repository.ReservationChangeReviewRepository;
import org.example.springtestci.trade.repository.ReservationChangeReviewRepository.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReservationChangeServiceTest {

  private static final long REQUEST_ID = 41L;
  private static final long EVENT_ID = 42L;
  private static final long ROOM_ID = 51L;
  private static final long LISTING_ID = 61L;
  private static final long PRODUCT_ID = 71L;
  private static final long SELLER_ID = 81L;
  private static final long BUYER_ID = 91L;
  private static final Instant NOW = Instant.parse("2026-10-26T02:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private PurchaseRequestRepository requestRepository;
  @Mock private ReservationChangeReviewRepository reviewRepository;
  @Mock private ChatRoomRepository roomRepository;
  @Mock private PublishedListingAccess listingAccess;
  @Mock private TradeCancellationService cancellationService;

  private ReservationChangeService service;

  @BeforeEach
  void setUp() {
    service =
        new ReservationChangeService(
            requestRepository,
            reviewRepository,
            roomRepository,
            listingAccess,
            cancellationService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void recordsNewChangeAndClearsCompletionVersions() {
    PurchaseRequest request = reservedRequest();
    givenReservedConnection(request);
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.empty());
    when(reviewRepository.saveIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);

    var response = service.recordChange(PRODUCT_ID, REQUEST_ID, EVENT_ID, 3L, facts("new"));

    assertThat(response.eventId()).isEqualTo(EVENT_ID);
    assertThat(response.factsVersion()).isEqualTo(3L);
    assertThat(response.decision()).isNull();
    verify(requestRepository).clearConfirmedVersions(REQUEST_ID);
  }

  @Test
  void duplicateChangeKeepsOriginalSnapshotWithoutClearingAgain() {
    JsonNode facts = facts("same");
    ReservationChangeReview existing =
        ReservationChangeReview.pending(REQUEST_ID, EVENT_ID, 3L, facts);
    PurchaseRequest request = reservedRequest();
    givenReservedConnection(request);
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(existing));

    var response = service.recordChange(PRODUCT_ID, REQUEST_ID, EVENT_ID, 3L, facts);

    assertThat(response.changeFacts()).isEqualTo(facts);
    verify(reviewRepository, never()).saveIfAbsent(org.mockito.ArgumentMatchers.any());
    verify(requestRepository, never()).clearConfirmedVersions(REQUEST_ID);
  }

  @Test
  void rejectsDifferentSnapshotForSameEvent() {
    ReservationChangeReview existing =
        ReservationChangeReview.pending(REQUEST_ID, EVENT_ID, 3L, facts("old"));
    PurchaseRequest request = reservedRequest();
    givenReservedConnection(request);
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(existing));

    assertError(
        ErrorCode.TRADE_CONFLICT,
        () -> service.recordChange(PRODUCT_ID, REQUEST_ID, EVENT_ID, 4L, facts("new")));
  }

  @Test
  void buyerKeepsOneChangeWithoutCompletingTrade() {
    PurchaseRequest request = reservedRequest();
    ReservationChangeReview pending = pendingReview();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(pending));
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(reservedListing()));
    when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
    when(reviewRepository.findForUpdate(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(pending));

    var response = service.review(REQUEST_ID, EVENT_ID, BUYER_ID, ChangeReviewDecision.KEEP, null);

    assertThat(response.decision()).isEqualTo(ChangeReviewDecision.KEEP);
    assertThat(response.reviewedAt()).isEqualTo(NOW);
    assertThat(request.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
    verify(reviewRepository).updateDecision(pending);
    verifyNoInteractions(cancellationService);
  }

  @Test
  void buyerCancellationDecidesReviewAndUsesCommonCancellationFlow() {
    PurchaseRequest request = reservedRequest();
    ReservationChangeReview pending = pendingReview();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(pending));
    when(reviewRepository.findForUpdate(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(pending));

    var response =
        service.review(
            REQUEST_ID,
            EVENT_ID,
            BUYER_ID,
            ChangeReviewDecision.CANCEL,
            TradeCancellationReason.CONDITION_MISMATCH);

    assertThat(response.decision()).isEqualTo(ChangeReviewDecision.CANCEL);
    verify(cancellationService)
        .cancelForChangeReview(REQUEST_ID, BUYER_ID, TradeCancellationReason.CONDITION_MISMATCH);
    verify(reviewRepository).updateDecision(pending);
  }

  @Test
  void doesNotWriteLateCancelDecisionWhenTradeWasAlreadyCancelled() {
    PurchaseRequest request = reservedRequest();
    ReservationChangeReview pending = pendingReview();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(pending));
    org.mockito.Mockito.doThrow(new CommonException(ErrorCode.TRADE_CONFLICT))
        .when(cancellationService)
        .cancelForChangeReview(REQUEST_ID, BUYER_ID, TradeCancellationReason.CONDITION_MISMATCH);

    assertError(
        ErrorCode.TRADE_CONFLICT,
        () ->
            service.review(
                REQUEST_ID,
                EVENT_ID,
                BUYER_ID,
                ChangeReviewDecision.CANCEL,
                TradeCancellationReason.CONDITION_MISMATCH));

    verify(reviewRepository, never()).updateDecision(pending);
  }

  @Test
  void repeatedSameDecisionReturnsStoredResultAndDifferentDecisionConflicts() {
    PurchaseRequest request = reservedRequest();
    ReservationChangeReview kept = pendingReview();
    kept.decide(ChangeReviewDecision.KEEP, NOW);
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(reviewRepository.find(REQUEST_ID, EVENT_ID)).thenReturn(Optional.of(kept));

    assertThat(
            service
                .review(REQUEST_ID, EVENT_ID, BUYER_ID, ChangeReviewDecision.KEEP, null)
                .reviewedAt())
        .isEqualTo(NOW);
    assertError(
        ErrorCode.TRADE_CONFLICT,
        () ->
            service.review(
                REQUEST_ID,
                EVENT_ID,
                BUYER_ID,
                ChangeReviewDecision.CANCEL,
                TradeCancellationReason.CONDITION_MISMATCH));

    verify(reviewRepository, never()).updateDecision(kept);
    verifyNoInteractions(cancellationService);
  }

  @Test
  void sellerCannotRespondAndOutsiderCannotSeeTrade() {
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(reservedRequest()));
    givenRoom();

    assertError(
        ErrorCode.ACCESS_DENIED,
        () -> service.review(REQUEST_ID, EVENT_ID, SELLER_ID, ChangeReviewDecision.KEEP, null));
    assertError(
        ErrorCode.RESOURCE_NOT_FOUND,
        () -> service.review(REQUEST_ID, EVENT_ID, 999L, ChangeReviewDecision.KEEP, null));
  }

  @Test
  void validatesDecisionReasonPair() {
    assertError(
        ErrorCode.INVALID_INPUT,
        () -> service.review(REQUEST_ID, EVENT_ID, BUYER_ID, ChangeReviewDecision.CANCEL, null));
    assertError(
        ErrorCode.INVALID_INPUT,
        () ->
            service.review(
                REQUEST_ID,
                EVENT_ID,
                BUYER_ID,
                ChangeReviewDecision.KEEP,
                TradeCancellationReason.SELF_CIRCUMSTANCES));
  }

  @Test
  void returnsPagedReviewsAndUnreviewedCountToEitherParty() {
    PurchaseRequest request = reservedRequest();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(reviewRepository.findAll(REQUEST_ID, 0, 20))
        .thenReturn(new PageResult(List.of(pendingReview()), 1));
    when(reviewRepository.countUnreviewed(REQUEST_ID)).thenReturn(1L);

    var response = service.findAll(REQUEST_ID, SELLER_ID, 0, 20);

    assertThat(response.items()).hasSize(1);
    assertThat(response.unreviewedCount()).isEqualTo(1L);
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
  }

  private void givenReservedConnection(PurchaseRequest request) {
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(reservedListing()));
    when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
  }

  private void givenRoom() {
    when(roomRepository.findById(ROOM_ID))
        .thenReturn(Optional.of(new ChatRoom(ROOM_ID, LISTING_ID, SELLER_ID, BUYER_ID, NOW)));
  }

  private PurchaseRequest reservedRequest() {
    return PurchaseRequest.restore(
        REQUEST_ID,
        ROOM_ID,
        LISTING_ID,
        PurchaseRequestStatus.ACCEPTED,
        TransactionStatus.RESERVED,
        2L,
        2L,
        null,
        null,
        null);
  }

  private PublishedListing reservedListing() {
    return new PublishedListing(
        LISTING_ID, PRODUCT_ID, SELLER_ID, SELLER_ID, PublishedListingStatus.RESERVED);
  }

  private ReservationChangeReview pendingReview() {
    return ReservationChangeReview.pending(REQUEST_ID, EVENT_ID, 3L, facts("changed"));
  }

  private JsonNode facts(String title) {
    return JSON.readTree("{\"title\":\"" + title + "\"}");
  }

  private void assertError(ErrorCode errorCode, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            CommonException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
  }
}
