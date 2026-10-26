package org.example.springtestci.trade.repository;

import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.entity.ReservationChangeReview;

public interface ReservationChangeReviewRepository {

  Optional<ReservationChangeReview> find(long purchaseRequestId, long eventId);

  Optional<ReservationChangeReview> findForUpdate(long purchaseRequestId, long eventId);

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
