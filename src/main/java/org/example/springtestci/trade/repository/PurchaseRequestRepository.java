package org.example.springtestci.trade.repository;

import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;

/** P2/P3 구매 요청 상태와 조건부 잠금·갱신을 제공하는 영속성 포트다. */
public interface PurchaseRequestRepository {

  Optional<PurchaseRequest> findById(long requestId);

  Optional<PurchaseRequest> findByIdForUpdate(long requestId);

  Optional<PurchaseRequest> findPendingByRoomId(long roomId);

  Optional<PurchaseRequest> findReservedByListingId(long listingId);

  /** 판매글에 연결된 모든 요청을 식별자 순으로 잠가 판매 중지 경합의 잠금 순서를 고정한다. */
  List<PurchaseRequest> findByListingIdForUpdate(long listingId);

  PurchaseRequest save(PurchaseRequest request);

  void update(PurchaseRequest request);

  /** ACCEPTED/RESERVED 한 건만 취소 메타데이터와 함께 조건부 갱신한다. */
  void cancelReserved(PurchaseRequest request);

  void clearConfirmedVersions(long requestId);

  /** ACCEPTED/RESERVED인 판매자의 완료 확인 버전만 조건부 저장한다. */
  void confirmSeller(long requestId, long factsVersion);

  /** ACCEPTED/RESERVED인 선택 구매자의 완료 확인 버전만 조건부 저장한다. */
  void confirmBuyer(long requestId, long factsVersion);

  /** 양측 확인값이 같은 최신 버전인 요청 하나만 COMPLETED로 조건부 전환한다. */
  void complete(long requestId, long factsVersion);

  /** 완료된 요청을 제외한 같은 판매글의 PENDING 요청만 REJECTED로 정리한다. */
  int rejectPendingByListingId(long listingId, long completedRequestId);

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
