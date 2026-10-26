package org.example.springtestci.trade.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingTradeCanceller.Command;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeListingCancellerTest {

  private static final long LISTING_ID = 61L;
  private static final long PRODUCT_ID = 71L;
  private static final long SELLER_ID = 81L;
  private static final long BUYER_ID = 91L;
  private static final long ROOM_ID = 51L;

  @Mock private PurchaseRequestRepository requestRepository;
  @Mock private ChatRoomRepository roomRepository;
  @Mock private PublishedListingAccess listingAccess;
  @Mock private TradeCancellationService cancellationService;

  private TradeListingCanceller canceller;

  @BeforeEach
  void setUp() {
    canceller =
        new TradeListingCanceller(
            requestRepository, roomRepository, listingAccess, cancellationService);
  }

  @Test
  void activeListingCancellationCancelsEveryPendingRequest() {
    PurchaseRequest first = pending(1L, ROOM_ID);
    PurchaseRequest second = pending(2L, ROOM_ID + 1);
    givenCancelledListing();
    when(requestRepository.findByListingIdForUpdate(LISTING_ID)).thenReturn(List.of(first, second));

    canceller.cancel(command(ListingStatus.ACTIVE, "free text"));

    verify(requestRepository).update(first);
    verify(requestRepository).update(second);
    verify(cancellationService, never())
        .cancelForListingShutdown(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any());
    verify(listingAccess, never()).releaseReservation(LISTING_ID);
  }

  @Test
  void reservedListingCancellationCancelsSelectedTradeAndOtherPendingRequests() {
    PurchaseRequest selected = reserved(1L, ROOM_ID);
    PurchaseRequest pending = pending(2L, ROOM_ID + 1);
    PublishedListing listing = givenCancelledListing();
    when(requestRepository.findByListingIdForUpdate(LISTING_ID))
        .thenReturn(List.of(selected, pending));
    ChatRoom room = new ChatRoom(ROOM_ID, LISTING_ID, SELLER_ID, BUYER_ID, Instant.EPOCH);
    when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    canceller.cancel(command(ListingStatus.RESERVED, "MUTUAL_AGREEMENT"));

    verify(cancellationService)
        .cancelForListingShutdown(
            selected, room, listing, SELLER_ID, TradeCancellationReason.MUTUAL_AGREEMENT);
    verify(requestRepository).update(pending);
    verify(listingAccess, never()).releaseReservation(LISTING_ID);
  }

  @Test
  void reservedListingRejectsUnmappedFreeTextReason() {
    givenCancelledListing();
    when(requestRepository.findByListingIdForUpdate(LISTING_ID))
        .thenReturn(List.of(reserved(1L, ROOM_ID)));

    assertError(
        ErrorCode.LISTING_CANCEL_UNAVAILABLE,
        () -> canceller.cancel(command(ListingStatus.RESERVED, "buyer changed mind")));
  }

  @Test
  void completedTradeBlocksListingCancellation() {
    givenCancelledListing();
    PurchaseRequest completed =
        PurchaseRequest.restore(
            1L, ROOM_ID, LISTING_ID, PurchaseRequestStatus.ACCEPTED, TransactionStatus.COMPLETED);
    when(requestRepository.findByListingIdForUpdate(LISTING_ID)).thenReturn(List.of(completed));

    assertError(
        ErrorCode.TRADE_CONFLICT,
        () -> canceller.cancel(command(ListingStatus.ACTIVE, "free text")));
  }

  private PublishedListing givenCancelledListing() {
    PublishedListing listing =
        new PublishedListing(
            LISTING_ID, PRODUCT_ID, SELLER_ID, SELLER_ID, PublishedListingStatus.CANCELLED);
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));
    return listing;
  }

  private Command command(ListingStatus previousStatus, String reason) {
    return new Command(PRODUCT_ID, LISTING_ID, SELLER_ID, previousStatus, reason);
  }

  private PurchaseRequest pending(long requestId, long roomId) {
    return PurchaseRequest.restore(
        requestId, roomId, LISTING_ID, PurchaseRequestStatus.PENDING, null);
  }

  private PurchaseRequest reserved(long requestId, long roomId) {
    return PurchaseRequest.restore(
        requestId, roomId, LISTING_ID, PurchaseRequestStatus.ACCEPTED, TransactionStatus.RESERVED);
  }

  private void assertError(ErrorCode errorCode, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            CommonException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                    .isEqualTo(errorCode));
  }
}
