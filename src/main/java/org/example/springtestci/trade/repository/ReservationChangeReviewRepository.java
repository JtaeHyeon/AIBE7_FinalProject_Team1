package org.example.springtestci.trade.repository;

import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.entity.ReservationChangeReview;

/** `(purchase_request_id, event_id)` 불변 변경 사본과 구매자 결정을 저장하는 P3 영속성 포트다. */
public interface ReservationChangeReviewRepository {

  Optional<ReservationChangeReview> find(long purchaseRequestId, long eventId);

  /** 동시 응답을 직렬화하기 위해 해당 review 행을 잠근다. */
  Optional<ReservationChangeReview> findForUpdate(long purchaseRequestId, long eventId);

  /** 기존 사본을 덮어쓰지 않고 최초 사건 사본만 저장한다. */
  boolean saveIfAbsent(ReservationChangeReview review);

  void updateDecision(ReservationChangeReview review);

  PageResult findAll(long purchaseRequestId, int page, int size);

  long countUnreviewed(long purchaseRequestId);

  record PageResult(List<ReservationChangeReview> items, long totalElements) {

    public PageResult {
      items = List.copyOf(items);
    }
  }
}
