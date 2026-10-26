package org.example.springtestci.trade.service;

import java.util.List;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.ListingTradeCanceller;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class TradeListingCanceller implements ListingTradeCanceller {

  private final PurchaseRequestRepository requestRepository;
  private final ChatRoomRepository roomRepository;
  private final PublishedListingAccess listingAccess;
  private final TradeCancellationService cancellationService;

  public TradeListingCanceller(
      PurchaseRequestRepository requestRepository,
      ChatRoomRepository roomRepository,
      PublishedListingAccess listingAccess,
      TradeCancellationService cancellationService) {
    this.requestRepository = requestRepository;
    this.roomRepository = roomRepository;
    this.listingAccess = listingAccess;
    this.cancellationService = cancellationService;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void cancel(Command command) {
    PublishedListing listing =
        listingAccess
            .findByIdForUpdate(command.listingId())
            .filter(
                value ->
                    value.productId() == command.productId()
                        && value.sellerId() == command.sellerId()
                        && value.currentOwnerId() == command.sellerId()
                        && value.status() == PublishedListingStatus.CANCELLED)
            .orElseThrow(() -> new CommonException(ErrorCode.TRADE_CONFLICT));
    List<PurchaseRequest> requests =
        requestRepository.findByListingIdForUpdate(command.listingId());
    rejectCompleted(requests);

    if (command.status() == ListingStatus.ACTIVE) {
      if (requests.stream().anyMatch(PurchaseRequest::isReserved)) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
    } else if (command.status() == ListingStatus.RESERVED) {
      TradeCancellationReason reason = parseReservedReason(command.reason());
      List<PurchaseRequest> reserved =
          requests.stream().filter(PurchaseRequest::isReserved).toList();
      if (reserved.size() != 1) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      PurchaseRequest selected = reserved.get(0);
      ChatRoom room = requireRoom(selected.roomId());
      cancellationService.cancelForListingShutdown(
          selected, room, listing, command.sellerId(), reason);
    } else {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    requests.stream()
        .filter(request -> request.requestStatus() == PurchaseRequestStatus.PENDING)
        .forEach(
            request -> {
              request.cancelPending();
              requestRepository.update(request);
            });
  }

  private void rejectCompleted(List<PurchaseRequest> requests) {
    if (requests.stream()
        .anyMatch(request -> request.transactionStatus() == TransactionStatus.COMPLETED)) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private TradeCancellationReason parseReservedReason(String reason) {
    try {
      return TradeCancellationReason.valueOf(reason);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new CommonException(ErrorCode.LISTING_CANCEL_UNAVAILABLE);
    }
  }

  private ChatRoom requireRoom(long roomId) {
    return roomRepository
        .findById(roomId)
        .orElseThrow(() -> new CommonException(ErrorCode.TRADE_CONFLICT));
  }
}
