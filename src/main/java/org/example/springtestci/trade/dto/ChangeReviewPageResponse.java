package org.example.springtestci.trade.dto;

import java.util.List;
import org.example.springtestci.trade.entity.TransactionStatus;

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
