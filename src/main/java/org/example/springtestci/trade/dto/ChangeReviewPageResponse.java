package org.example.springtestci.trade.dto;

import java.util.List;
import org.example.springtestci.trade.entity.TransactionStatus;

/** 변경 review 페이지와 전체 미확인 건수, 현재 예약 거래 상태를 함께 반환한다. */
public record ChangeReviewPageResponse(
    List<ChangeReviewResponse> items,
    PageMetadata page,
    long unreviewedCount,
    TransactionStatus transactionStatus) {

  public ChangeReviewPageResponse {
    items = List.copyOf(items);
  }

  public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
}
