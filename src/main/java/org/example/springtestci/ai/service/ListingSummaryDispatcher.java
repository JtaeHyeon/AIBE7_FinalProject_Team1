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

  @Scheduled(
      fixedDelayString = "${app.ai.summary.dispatch-delay-ms:1000}",
      initialDelayString = "${app.ai.summary.initial-delay-ms:1000}")
  public void dispatch() {
    try {
      store.recoverExpired();
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
          submitted.remove(pending.attemptId());
        }
      }
    } catch (RuntimeException failure) {
      LOG.warn("AI summary dispatch unavailable type={}", failure.getClass().getSimpleName());
    }
  }
}
