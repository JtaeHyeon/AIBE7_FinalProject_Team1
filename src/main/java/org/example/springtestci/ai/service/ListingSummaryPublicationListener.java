package org.example.springtestci.ai.service;

import org.example.springtestci.listing.event.ListingPublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 게시 트랜잭션이 성공한 뒤에만 요약을 접수한다. 게시가 롤백되면 AI 작업도 시작하지 않는다. */
@Component
public class ListingSummaryPublicationListener {
  private static final Logger LOG =
      LoggerFactory.getLogger(ListingSummaryPublicationListener.class);
  private final ListingSummaryStore store;

  public ListingSummaryPublicationListener(ListingSummaryStore store) {
    this.store = store;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void published(ListingPublished event) {
    try {
      store.enqueue(event.listingId(), null);
    } catch (RuntimeException failure) {
      // 게시 성공은 유지한다. 미접수 상태는 판매자 화면에서 명시적으로 다시 요청할 수 있다.
      LOG.warn(
          "AI summary enqueue unavailable listingId={}, type={}",
          event.listingId(),
          failure.getClass().getSimpleName());
    }
  }
}
