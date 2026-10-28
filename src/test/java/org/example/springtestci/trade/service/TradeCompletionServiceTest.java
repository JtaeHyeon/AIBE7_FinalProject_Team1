package org.example.springtestci.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingDetailService;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.service.ProductService;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeCompletionServiceTest {

  private static final long REQUEST_ID = 41L;
  private static final long ROOM_ID = 51L;
  private static final long LISTING_ID = 61L;
  private static final long PRODUCT_ID = 71L;
  private static final long SELLER_ID = 81L;
  private static final long BUYER_ID = 91L;
  private static final long FACTS_VERSION = 7L;
  private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");

  @Mock private PurchaseRequestRepository requestRepository;
  @Mock private ChatRoomRepository roomRepository;
  @Mock private PublishedListingAccess listingAccess;
  @Mock private ProductService productService;
  @Mock private ListingDetailService listingDetailService;
  @Mock private ReservationChangeService changeService;
  @Mock private InternalTradeCompletionPort completionPort;
  @Mock private TradeAuditRecorder auditRecorder;

  private TradeCompletionService service;

  @BeforeEach
  void setUp() {
    service =
        new TradeCompletionService(
            requestRepository,
            roomRepository,
            listingAccess,
            productService,
            listingDetailService,
            changeService,
            completionPort,
            auditRecorder,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(longs = {SELLER_ID, BUYER_ID})
  void storesOnlyActorsConfirmationAndWaitsForOtherParty(long actorId) {
    PurchaseRequest request = reservedRequest(null, null);
    givenInProgress(request, officialDetail(false));

    var response = service.complete(REQUEST_ID, actorId, FACTS_VERSION);

    assertThat(response.completed()).isFalse();
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
    if (actorId == SELLER_ID) {
      assertThat(response.sellerConfirmedVersion()).isEqualTo(FACTS_VERSION);
      assertThat(response.buyerConfirmedVersion()).isNull();
      verify(requestRepository).confirmSeller(REQUEST_ID, FACTS_VERSION);
      verify(requestRepository, never()).confirmBuyer(REQUEST_ID, FACTS_VERSION);
    } else {
      assertThat(response.buyerConfirmedVersion()).isEqualTo(FACTS_VERSION);
      assertThat(response.sellerConfirmedVersion()).isNull();
      verify(requestRepository).confirmBuyer(REQUEST_ID, FACTS_VERSION);
      verify(requestRepository, never()).confirmSeller(REQUEST_ID, FACTS_VERSION);
    }
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void lastConfirmationCompletesEveryTradeSideEffectInOrder() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, null);
    givenInProgress(request, officialDetail(false));

    var response = service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION);

    assertThat(response.completed()).isTrue();
    assertThat(response.requestStatus()).isEqualTo(PurchaseRequestStatus.ACCEPTED);
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(response.listingStatus()).isEqualTo(PublishedListingStatus.SOLD);
    InOrder order = inOrder(requestRepository, listingAccess, completionPort, auditRecorder);
    order.verify(requestRepository).confirmBuyer(REQUEST_ID, FACTS_VERSION);
    order.verify(requestRepository).complete(REQUEST_ID, FACTS_VERSION);
    order.verify(listingAccess).sell(LISTING_ID);
    order.verify(requestRepository).rejectPendingByListingId(LISTING_ID, REQUEST_ID);
    order
        .verify(completionPort)
        .complete(
            new InternalTradeCompletionPort.Command(
                PRODUCT_ID,
                REQUEST_ID,
                LISTING_ID,
                SELLER_ID,
                BUYER_ID,
                BUYER_ID,
                FACTS_VERSION,
                NOW));
    order
        .verify(auditRecorder)
        .tradeCompleted(PRODUCT_ID, BUYER_ID, REQUEST_ID, FACTS_VERSION, NOW);
  }

  @Test
  void sellerCanAlsoProvideLastConfirmation() {
    PurchaseRequest request = reservedRequest(null, FACTS_VERSION);
    givenInProgress(request, officialDetail(false));

    var response = service.complete(REQUEST_ID, SELLER_ID, FACTS_VERSION);

    assertThat(response.completed()).isTrue();
    verify(requestRepository).confirmSeller(REQUEST_ID, FACTS_VERSION);
    verify(completionPort)
        .complete(
            new InternalTradeCompletionPort.Command(
                PRODUCT_ID,
                REQUEST_ID,
                LISTING_ID,
                SELLER_ID,
                BUYER_ID,
                SELLER_ID,
                FACTS_VERSION,
                NOW));
  }

  @Test
  void concurrentCompletionFoundAfterLockReturnsStoredResultWithoutSideEffects() {
    PurchaseRequest initial = reservedRequest(FACTS_VERSION, null);
    PurchaseRequest completed = completedRequest();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(initial));
    givenRoom();
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(reservedListing()));
    when(requestRepository.findByListingIdForUpdate(LISTING_ID)).thenReturn(List.of(completed));

    var response = service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION);

    assertThat(response.completed()).isTrue();
    verifyNoInteractions(productService, listingDetailService, changeService);
    verify(listingAccess, never()).sell(LISTING_ID);
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void repeatedCompletedRequestReturnsStoredMinimumWithoutCurrentProductChecks() {
    PurchaseRequest completed = completedRequest();
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(completed));
    givenRoom();

    var response = service.complete(REQUEST_ID, SELLER_ID, FACTS_VERSION);

    assertThat(response.completed()).isTrue();
    assertThat(response.listingStatus()).isEqualTo(PublishedListingStatus.SOLD);
    verifyNoInteractions(listingAccess, productService, listingDetailService, changeService);
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void staleVersionDoesNotStoreConfirmation() {
    PurchaseRequest request = reservedRequest(null, null);
    givenInProgress(request, officialDetail(false));

    assertError(ErrorCode.TRADE_CONFLICT, () -> service.complete(REQUEST_ID, BUYER_ID, 6L));

    verify(requestRepository, never()).confirmBuyer(anyLong(), anyLong());
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void duplicateConfirmationDoesNotReplaceOtherPartysConsentOrCompleteTrade() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, null);
    givenInProgress(request, officialDetail(false));

    var response = service.complete(REQUEST_ID, SELLER_ID, FACTS_VERSION);

    assertThat(response.completed()).isFalse();
    assertThat(response.buyerConfirmedVersion()).isNull();
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void unreviewedChangeBlocksConfirmationEvenAfterOfficialContextLoads() {
    PurchaseRequest request = reservedRequest(null, null);
    givenInProgress(request, officialDetail(false));
    doThrow(new CommonException(ErrorCode.TRADE_CONFLICT))
        .when(changeService)
        .requireNoUnreviewedChanges(REQUEST_ID);

    assertError(
        ErrorCode.TRADE_CONFLICT, () -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION));

    verify(requestRepository, never()).confirmBuyer(anyLong(), anyLong());
  }

  @Test
  void sampleHistoryCannotBeUsedForContextOrConfirmation() {
    PurchaseRequest request = reservedRequest(null, null);
    givenInProgress(request, officialDetail(true));

    assertError(
        ErrorCode.LISTING_HISTORY_UNAVAILABLE,
        () -> service.getContext(REQUEST_ID, BUYER_ID, 0, 20));
    assertError(
        ErrorCode.LISTING_HISTORY_UNAVAILABLE,
        () -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION));

    verify(requestRepository, never()).confirmBuyer(anyLong(), anyLong());
  }

  @Test
  void contextUsesCurrentVersionFlagsAndPublicProjection() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, 6L);
    ListingDetailResponse detail = officialDetail(false);
    givenInProgress(request, detail);
    when(changeService.countUnreviewedChanges(REQUEST_ID)).thenReturn(2L);

    var response = service.getContext(REQUEST_ID, SELLER_ID, 1, 10);

    assertThat(response.factsVersion()).isEqualTo(FACTS_VERSION);
    assertThat(response.sellerConfirmedCurrentVersion()).isTrue();
    assertThat(response.buyerConfirmedCurrentVersion()).isFalse();
    assertThat(response.unreviewedChangeCount()).isEqualTo(2);
    assertThat(response.completionAllowed()).isFalse();
    assertThat(response.listing()).isSameAs(detail);
    verify(listingDetailService).get(LISTING_ID, SELLER_ID, 1, 10);
  }

  @Test
  void hidesTradeFromOutsiderBeforeTakingLocks() {
    when(requestRepository.findById(REQUEST_ID))
        .thenReturn(Optional.of(reservedRequest(null, null)));
    givenRoom();

    assertError(
        ErrorCode.RESOURCE_NOT_FOUND, () -> service.complete(REQUEST_ID, 999L, FACTS_VERSION));

    verifyNoInteractions(listingAccess, completionPort, auditRecorder);
  }

  @Test
  void rejectsCancelledOrPendingTradeBeforeTakingLocks() {
    for (PurchaseRequest request :
        List.of(
            PurchaseRequest.restore(
                REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.CANCELLED, null),
            PurchaseRequest.restore(
                REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.PENDING, null))) {
      when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
      givenRoom();

      assertError(
          ErrorCode.TRADE_CONFLICT, () -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION));
    }

    verifyNoInteractions(listingAccess, completionPort, auditRecorder);
  }

  @Test
  void rejectsMismatchedCurrentOwnerBeforeReadingProductDetails() {
    PurchaseRequest request = reservedRequest(null, null);
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(listingAccess.findByIdForUpdate(LISTING_ID))
        .thenReturn(
            Optional.of(
                new PublishedListing(
                    LISTING_ID, PRODUCT_ID, SELLER_ID, BUYER_ID, PublishedListingStatus.RESERVED)));
    when(requestRepository.findByListingIdForUpdate(LISTING_ID)).thenReturn(List.of(request));

    assertError(
        ErrorCode.TRADE_CONFLICT, () -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION));

    verifyNoInteractions(productService, listingDetailService, completionPort, auditRecorder);
  }

  @Test
  void tradeStateWriteFailurePreventsLaterCompletionSideEffects() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, null);
    givenInProgress(request, officialDetail(false));
    doThrow(new CommonException(ErrorCode.TRADE_CONFLICT))
        .when(requestRepository)
        .complete(REQUEST_ID, FACTS_VERSION);

    assertError(
        ErrorCode.TRADE_CONFLICT, () -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION));

    verify(listingAccess, never()).sell(LISTING_ID);
    verifyNoInteractions(completionPort, auditRecorder);
  }

  @Test
  void ownershipFailureStopsAuditAndIsPropagatedForTransactionRollback() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, null);
    givenInProgress(request, officialDetail(false));
    doThrow(new IllegalStateException("ownership failure")).when(completionPort).complete(any());

    assertThatThrownBy(() -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION))
        .isInstanceOf(IllegalStateException.class);

    verify(auditRecorder, never())
        .tradeCompleted(anyLong(), anyLong(), anyLong(), anyLong(), any());
  }

  @Test
  void auditFailureIsPropagatedAfterCompletionPortForTransactionRollback() {
    PurchaseRequest request = reservedRequest(FACTS_VERSION, null);
    givenInProgress(request, officialDetail(false));
    doThrow(new IllegalStateException("audit failure"))
        .when(auditRecorder)
        .tradeCompleted(PRODUCT_ID, BUYER_ID, REQUEST_ID, FACTS_VERSION, NOW);

    assertThatThrownBy(() -> service.complete(REQUEST_ID, BUYER_ID, FACTS_VERSION))
        .isInstanceOf(IllegalStateException.class);

    InOrder order = inOrder(completionPort, auditRecorder);
    order.verify(completionPort).complete(any());
    order
        .verify(auditRecorder)
        .tradeCompleted(PRODUCT_ID, BUYER_ID, REQUEST_ID, FACTS_VERSION, NOW);
  }

  private void givenInProgress(PurchaseRequest request, ListingDetailResponse detail) {
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(reservedListing()));
    when(requestRepository.findByListingIdForUpdate(LISTING_ID)).thenReturn(List.of(request));
    givenRoom();
    when(productService.getForListing(SELLER_ID, PRODUCT_ID)).thenReturn(product());
    when(listingDetailService.get(
            org.mockito.ArgumentMatchers.eq(LISTING_ID),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(detail);
  }

  private void givenRoom() {
    when(roomRepository.findById(ROOM_ID))
        .thenReturn(Optional.of(new ChatRoom(ROOM_ID, LISTING_ID, SELLER_ID, BUYER_ID, NOW)));
  }

  private PurchaseRequest reservedRequest(Long sellerVersion, Long buyerVersion) {
    return PurchaseRequest.restore(
        REQUEST_ID,
        ROOM_ID,
        LISTING_ID,
        PurchaseRequestStatus.ACCEPTED,
        TransactionStatus.RESERVED,
        sellerVersion,
        buyerVersion,
        null,
        null,
        null);
  }

  private PurchaseRequest completedRequest() {
    return PurchaseRequest.restore(
        REQUEST_ID,
        ROOM_ID,
        LISTING_ID,
        PurchaseRequestStatus.ACCEPTED,
        TransactionStatus.COMPLETED,
        FACTS_VERSION,
        FACTS_VERSION,
        null,
        null,
        null);
  }

  private PublishedListing reservedListing() {
    return new PublishedListing(
        LISTING_ID, PRODUCT_ID, SELLER_ID, SELLER_ID, PublishedListingStatus.RESERVED);
  }

  private ProductResponse product() {
    return new ProductResponse(
        PRODUCT_ID,
        "노트북",
        "제조사",
        "모델",
        null,
        null,
        null,
        FACTS_VERSION,
        null,
        null,
        null,
        List.of(),
        null);
  }

  private ListingDetailResponse officialDetail(boolean sample) {
    return new ListingDetailResponse(
        LISTING_ID,
        1L,
        ListingStatus.RESERVED,
        NOW,
        new ListingPreviewResponse.Content("제목", "설명", java.math.BigDecimal.valueOf(1000L), 1),
        List.of(),
        new ListingPublicationResponse.Product(PRODUCT_ID, "제조사", "모델", null, null, null),
        new ListingPublicationResponse.History(
            ListingHistoryReader.State.READY,
            sample,
            List.of(),
            new ListingPublicationResponse.Page(0, 20, 0, 0, false),
            0,
            0),
        true,
        null);
  }

  private void assertError(ErrorCode errorCode, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            CommonException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
  }
}
