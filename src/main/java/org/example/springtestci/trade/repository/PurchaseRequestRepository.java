package org.example.springtestci.trade.repository;

import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;

public interface PurchaseRequestRepository {

  Optional<PurchaseRequest> findById(long requestId);

  Optional<PurchaseRequest> findByIdForUpdate(long requestId);

  Optional<PurchaseRequest> findPendingByRoomId(long roomId);

  Optional<PurchaseRequest> findReservedByListingId(long listingId);

  List<PurchaseRequest> findByListingIdForUpdate(long listingId);

  PurchaseRequest save(PurchaseRequest request);

  void update(PurchaseRequest request);

  void cancelReserved(PurchaseRequest request);

  void clearConfirmedVersions(long requestId);

  PageResult findAll(
      long actorId,
      PurchaseRequestDirection direction,
      PurchaseRequestStatus status,
      Long roomId,
      int page,
      int size);

  record PageResult(List<PurchaseRequest> items, long totalElements) {

    public PageResult {
      items = List.copyOf(items);
    }
  }
}
