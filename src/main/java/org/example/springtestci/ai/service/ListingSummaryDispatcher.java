package org.example.springtestci.ai.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.example.springtestci.ai.repository.ListingSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** DB 대기열은 재시작 후에도 남는다. 인스턴스 간 중복 실행은 PROCESSING 전이의 조건부 변경으로 막는다. */
@Component
@ConditionalOnProperty(
    prefix = "app.ai.summary",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ListingSummaryDispatcher {
  private static final Logger LOG = LoggerFactory.getLogger(ListingSummaryDispatcher.class);
  // 이 서버의 작업 풀에 이미 제출한 시도 번호를 기억해 다음 조회에서 중복 제출하지 않는다.
  private final Set<UUID> submitted = ConcurrentHashMap.newKeySet();
  private final ListingSummaryStore store;
  private final ListingSummaryRepository repository;
  private final ListingSummaryProcessor processor;
  private final ThreadPoolTaskExecutor executor;

  public ListingSummaryDispatcher(
      ListingSummaryStore store,
      ListingSummaryRepository repository,
      ListingSummaryProcessor processor,
      @Qualifier("listingSummaryExecutor") ThreadPoolTaskExecutor executor) {
    this.store = store;
    this.repository = repository;
    this.processor = processor;
    this.executor = executor;
  }

  /** 대기열을 주기적으로 읽어 별도 작업 스레드에 넘긴다. 게시 API가 AI 응답을 기다리지 않게 한다. */
  @Scheduled(
      fixedDelayString = "${app.ai.summary.dispatch-delay-ms:1000}",
      initialDelayString = "${app.ai.summary.initial-delay-ms:1000}")
  public void dispatch() {
    try {
      store.recoverExpired();
      for (long listingId : repository.staleListings()) store.refresh(listingId);
      for (var pending : repository.pending()) {
        if (!submitted.add(pending.attemptId())) continue;
        try {
          executor.execute(
              () -> {
                try {
                  processor.process(pending);
                } finally {
                  submitted.remove(pending.attemptId());
                }
              });
        } catch (RejectedExecutionException busy) {
          // 실행 공간이 없으면 DB의 대기 상태를 유지해 다음 주기에 다시 가져온다.
          submitted.remove(pending.attemptId());
        }
      }
    } catch (RuntimeException failure) {
      LOG.warn("AI summary dispatch unavailable type={}", failure.getClass().getSimpleName());
    }
  }
}
