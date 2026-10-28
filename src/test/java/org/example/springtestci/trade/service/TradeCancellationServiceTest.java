package org.example.springtestci.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeCancellationServiceTest {

  private static final long REQUEST_ID = 41L;
  private static final long ROOM_ID = 51L;
  private static final long LISTING_ID = 61L;
  private static final long PRODUCT_ID = 71L;
  private static final long SELLER_ID = 81L;
  private static final long BUYER_ID = 91L;
  private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");

  @Mock private PurchaseRequestRepository requestRepository;
  @Mock private ChatRoomRepository roomRepository;
  @Mock private PublishedListingAccess listingAccess;
  @Mock private InternalTransferCancellationPort transferCancellation;
  @Mock private TradeAuditRecorder auditRecorder;

  private TradeCancellationService service;

  @BeforeEach
  void setUp() {
    service =
        new TradeCancellationService(
            requestRepository,
            roomRepository,
            listingAccess,
            transferCancellation,
            auditRecorder,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(longs = {SELLER_ID, BUYER_ID})
  void originalPartiesCancelReservation(long actorId) {
    PurchaseRequest request = reservedRequest();
    givenReserved(request);

    var response = service.cancel(REQUEST_ID, actorId, TradeCancellationReason.MUTUAL_AGREEMENT);

    assertThat(response.requestStatus()).isEqualTo(PurchaseRequestStatus.CANCELLED);
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.CANCELLED);
    assertThat(response.listingStatus()).isEqualTo(PublishedListingStatus.ACTIVE);
    assertThat(response.cancelledById()).isEqualTo(actorId);
    assertThat(response.cancelReason()).isEqualTo(TradeCancellationReason.MUTUAL_AGREEMENT);
    assertThat(response.cancelledAt()).isEqualTo(NOW);
    verify(transferCancellation)
        .cancelPending(
            new InternalTransferCancellationPort.Command(
                PRODUCT_ID, REQUEST_ID, SELLER_ID, BUYER_ID));
    verify(requestRepository).cancelReserved(request);
    verify(listingAccess).releaseReservation(LISTING_ID);
    verify(auditRecorder)
        .reservationCancelled(
            PRODUCT_ID, actorId, REQUEST_ID, TradeCancellationReason.MUTUAL_AGREEMENT, NOW);
  }

  @ParameterizedTest
  @EnumSource(TradeCancellationReason.class)
  void preservesEveryAllowedReason(TradeCancellationReason reason) {
    PurchaseRequest request = reservedRequest();
    givenReserved(request);

    assertThat(service.cancel(REQUEST_ID, BUYER_ID, reason).cancelReason()).isEqualTo(reason);
  }

  @Test
  void repeatedCancellationReturnsOriginalRecordWithoutSideEffects() {
    PurchaseRequest cancelled = reservedRequest();
    cancelled.cancelReserved(BUYER_ID, TradeCancellationReason.SELF_CIRCUMSTANCES, NOW);
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(cancelled));
    givenRoom();
    when(listingAccess.findById(LISTING_ID))
        .thenReturn(
            Optional.of(
                new PublishedListing(
                    LISTING_ID,
                    PRODUCT_ID,
                    SELLER_ID,
                    SELLER_ID,
                    PublishedListingStatus.RESERVED)));

    var response = service.cancel(REQUEST_ID, BUYER_ID, TradeCancellationReason.CONDITION_MISMATCH);

    assertThat(response.cancelReason()).isEqualTo(TradeCancellationReason.SELF_CIRCUMSTANCES);
    assertThat(response.listingStatus()).isEqualTo(PublishedListingStatus.RESERVED);
    verify(requestRepository, never()).findByIdForUpdate(REQUEST_ID);
    verify(listingAccess, never()).releaseReservation(LISTING_ID);
    verifyNoInteractions(transferCancellation, auditRecorder);
  }

  @Test
  void hidesTradeFromNonParticipant() {
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(reservedRequest()));
    givenRoom();

    assertError(
        ErrorCode.RESOURCE_NOT_FOUND,
        () -> service.cancel(REQUEST_ID, 999L, TradeCancellationReason.SELF_CIRCUMSTANCES));

    verifyNoInteractions(listingAccess, transferCancellation, auditRecorder);
  }

  @Test
  void doesNotTreatPendingCancellationAsReservedCancellation() {
    PurchaseRequest request =
        PurchaseRequest.restore(
            REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.CANCELLED, null);
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();

    assertError(
        ErrorCode.TRADE_CONFLICT,
        () -> service.cancel(REQUEST_ID, BUYER_ID, TradeCancellationReason.SELF_CIRCUMSTANCES));
  }

  @Test
  void propagatesTransferFailureBeforeOtherWrites() {
    PurchaseRequest request = reservedRequest();
    givenReserved(request);
    org.mockito.Mockito.doThrow(new IllegalStateException("ownership failure"))
        .when(transferCancellation)
        .cancelPending(any());

    assertThatThrownBy(
            () -> service.cancel(REQUEST_ID, BUYER_ID, TradeCancellationReason.CONDITION_MISMATCH))
        .isInstanceOf(IllegalStateException.class);

    verify(requestRepository, never()).cancelReserved(any());
    verify(listingAccess, never()).releaseReservation(LISTING_ID);
    verifyNoInteractions(auditRecorder);
  }

  private void givenReserved(PurchaseRequest request) {
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
    givenRoom();
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(reservedListing()));
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
        TransactionStatus.RESERVED);
  }

  private PublishedListing reservedListing() {
    return new PublishedListing(
        LISTING_ID, PRODUCT_ID, SELLER_ID, SELLER_ID, PublishedListingStatus.RESERVED);
  }

  private void assertError(ErrorCode errorCode, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            CommonException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
  }
}
