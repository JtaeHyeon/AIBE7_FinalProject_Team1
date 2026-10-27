package org.example.springtestci.trade.service;

import java.util.Map;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.dto.PurchaseRequestPageResponse;
import org.example.springtestci.trade.dto.PurchaseRequestPageResponse.PageMetadata;
import org.example.springtestci.trade.dto.PurchaseRequestPageResponse.RoomContext;
import org.example.springtestci.trade.dto.PurchaseRequestResponse;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.repository.PurchaseRequestRepository.PageResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
    prefix = "app.features.trade-purchase-requests",
    name = "enabled",
    havingValue = "true")
@Transactional(readOnly = true)
public class PurchaseRequestService {

  private final PurchaseRequestRepository requestRepository;
  private final ChatRoomRepository roomRepository;
  private final PublishedListingAccess listingAccess;
  private final InternalTransferPendingWriter transferWriter;

  public PurchaseRequestService(
      PurchaseRequestRepository requestRepository,
      ChatRoomRepository roomRepository,
      PublishedListingAccess listingAccess,
      InternalTransferPendingWriter transferWriter) {
    this.requestRepository = requestRepository;
    this.roomRepository = roomRepository;
    this.listingAccess = listingAccess;
    this.transferWriter = transferWriter;
  }

  @Transactional
  public CreateResult create(long listingId, long roomId, long actorId) {
    ChatRoom room = requireRoomParticipant(roomId, actorId);
    if (actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    requireRoomListing(room, listingId);

    Optional<PurchaseRequest> existing = requestRepository.findPendingByRoomId(roomId);
    if (existing.isPresent()) {
      return new CreateResult(toResponse(existing.get()), false);
    }

    PublishedListing listing = requireLockedListing(listingId);
    existing = requestRepository.findPendingByRoomId(roomId);
    if (existing.isPresent()) {
      return new CreateResult(toResponse(existing.get(), listing.status()), false);
    }
    requirePurchasable(listing, room, actorId);

    PurchaseRequest saved = requestRepository.save(PurchaseRequest.pending(roomId, listingId));
    return new CreateResult(toResponse(saved, listing.status()), true);
  }

  public PurchaseRequestPageResponse findAll(
      long actorId,
      PurchaseRequestDirection direction,
      PurchaseRequestStatus status,
      Long roomId,
      int page,
      int size) {
    ChatRoom room = roomId == null ? null : requireRoomParticipant(roomId, actorId);
    PageResult result = requestRepository.findAll(actorId, direction, status, roomId, page, size);
    Map<Long, PublishedListingStatus> statuses =
        listingAccess.findStatuses(
            result.items().stream().map(PurchaseRequest::listingId).distinct().toList());
    int totalPages =
        result.totalElements() == 0 ? 0 : (int) ((result.totalElements() + size - 1) / size);
    RoomContext roomContext =
        room == null
            ? null
            : new RoomContext(
                room.roomId(), room.listingId(), statusesForRoom(statuses, room.listingId()));
    return new PurchaseRequestPageResponse(
        result.items().stream()
            .map(request -> toResponse(request, statuses.get(request.listingId())))
            .toList(),
        new PageMetadata(page, size, result.totalElements(), totalPages),
        roomContext);
  }

  public PurchaseRequestResponse findById(long requestId, long actorId) {
    PurchaseRequest request = requireRequest(requestId);
    requireRoomParticipant(request.roomId(), actorId);
    return toResponse(request);
  }

  @Transactional
  public PurchaseRequestResponse cancel(long requestId, long actorId) {
    PurchaseRequest request = requireLockedRequest(requestId);
    ChatRoom room = requireRoomParticipant(request.roomId(), actorId);
    if (actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    request.cancelPending();
    requestRepository.update(request);
    return toResponse(request);
  }

  @Transactional
  public PurchaseRequestResponse reject(long requestId, long actorId) {
    PurchaseRequest request = requireLockedRequest(requestId);
    ChatRoom room = requireRoomParticipant(request.roomId(), actorId);
    if (actorId != room.sellerId()) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    request.reject();
    requestRepository.update(request);
    return toResponse(request);
  }

  @Transactional
  public PurchaseRequestResponse accept(long requestId, long actorId) {
    PurchaseRequest initial = requireRequest(requestId);
    ChatRoom room = requireRoomParticipant(initial.roomId(), actorId);
    if (actorId != room.sellerId()) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }

    PublishedListing listing = requireLockedListing(initial.listingId());
    PurchaseRequest request = requireLockedRequest(requestId);
    requireRoomListing(room, request.listingId());
    requirePurchasable(listing, room, room.inquirerId());
    if (requestRepository.findReservedByListingId(request.listingId()).isPresent()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }

    request.accept();
    requestRepository.update(request);
    listingAccess.reserve(listing.listingId());
    transferWriter.createPending(
        InternalTransferPendingWriter.Command.internalPending(
            listing.productId(), request.id(), listing.sellerId(), room.inquirerId()));
    return toResponse(request, PublishedListingStatus.RESERVED);
  }

  private ChatRoom requireRoomParticipant(long roomId, long actorId) {
    ChatRoom room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (actorId != room.sellerId() && actorId != room.inquirerId()) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return room;
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

  private void requireRoomListing(ChatRoom room, long listingId) {
    if (room.listingId() != listingId) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private void requirePurchasable(PublishedListing listing, ChatRoom room, long buyerId) {
    if (listing.listingId() != room.listingId()
        || listing.sellerId() != room.sellerId()
        || listing.currentOwnerId() != listing.sellerId()
        || listing.status() != PublishedListingStatus.ACTIVE
        || buyerId != room.inquirerId()
        || buyerId == listing.sellerId()) {
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    }
  }

  private PurchaseRequestResponse toResponse(PurchaseRequest request) {
    PublishedListingStatus status =
        listingAccess.findById(request.listingId()).map(PublishedListing::status).orElse(null);
    return toResponse(request, status);
  }

  private PurchaseRequestResponse toResponse(
      PurchaseRequest request, PublishedListingStatus listingStatus) {
    return new PurchaseRequestResponse(
        request.id(),
        request.roomId(),
        request.listingId(),
        request.requestStatus(),
        request.transactionStatus(),
        listingStatus);
  }

  private PublishedListingStatus statusesForRoom(
      Map<Long, PublishedListingStatus> statuses, long listingId) {
    if (statuses.containsKey(listingId)) {
      return statuses.get(listingId);
    }
    return listingAccess.findById(listingId).map(PublishedListing::status).orElse(null);
  }

  public record CreateResult(PurchaseRequestResponse response, boolean created) {}
}
