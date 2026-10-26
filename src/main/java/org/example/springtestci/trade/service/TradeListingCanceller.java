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
/**
 * R07 판매 중지 흐름에 P2/P3 거래 정리를 연결하는 어댑터다.
 *
 * <p>호출자가 판매글을 CANCELLED로 변경한 기존 트랜잭션 안에서만 실행된다. ACTIVE 판매글은 PENDING 요청을 정리하고, RESERVED 판매글은 선택 거래와
 * 나머지 PENDING 요청을 함께 취소하며 판매글을 ACTIVE로 되돌리지 않는다.
 */
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
  /** 독립 트랜잭션을 열지 않고 판매 중지 호출자의 원자 경계에 반드시 참여한다. */
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
