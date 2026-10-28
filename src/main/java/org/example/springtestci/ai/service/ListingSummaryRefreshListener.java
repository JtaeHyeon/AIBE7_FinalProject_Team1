package org.example.springtestci.ai.service;

import org.example.springtestci.listing.event.ListingContentChanged;
import org.example.springtestci.product.event.ProductFactsChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 변경 커밋 뒤 최신 버전의 요약을 접수한다. 접수 장애가 성공한 수정 요청을 오류로 바꾸지 않는다. */
@Component
public class ListingSummaryRefreshListener {
  private static final Logger LOG = LoggerFactory.getLogger(ListingSummaryRefreshListener.class);
  private final ListingSummaryStore store;

  public ListingSummaryRefreshListener(ListingSummaryStore store) {
    this.store = store;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void listingChanged(ListingContentChanged event) {
    refresh(event.listingId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void productChanged(ProductFactsChanged event) {
    try {
      for (long listingId : store.publicListingsForProduct(event.productId())) refresh(listingId);
    } catch (RuntimeException failure) {
      LOG.warn(
          "AI summary refresh lookup unavailable productId={}, type={}",
          event.productId(),
          failure.getClass().getSimpleName());
    }
  }

  private void refresh(long listingId) {
    try {
      store.refresh(listingId);
    } catch (RuntimeException failure) {
      LOG.warn(
          "AI summary refresh unavailable listingId={}, type={}",
          listingId,
          failure.getClass().getSimpleName());
    }
  }
}
