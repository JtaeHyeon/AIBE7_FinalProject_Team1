package org.example.springtestci.ai.service;

import org.example.springtestci.ai.repository.ListingSummaryRepository.Pending;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** DB 트랜잭션 밖에서 모델을 호출한다. 작업 오류를 안전한 상태로 저장하고 게시·상세 요청과 분리한다. */
@Service
public class ListingSummaryProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(ListingSummaryProcessor.class);
  private final ListingSummaryStore store;
  private final ListingSummaryGateway gateway;
  private final ListingSummaryTextPolicy policy;
  private final AiSuggestionRateLimiter limits;

  public ListingSummaryProcessor(
      ListingSummaryStore store,
      ListingSummaryGateway gateway,
      ListingSummaryTextPolicy policy,
      AiSuggestionRateLimiter limits) {
    this.store = store;
    this.gateway = gateway;
    this.policy = policy;
    this.limits = limits;
  }

  /** 작업 확보 → 요청 제한 → Gemini 생성 → 결과 검증 → 저장 순서로 한 건을 처리한다. */
  public void process(Pending pending) {
    AiSuggestionRateLimiter.Lease lease = null;
    try {
      var job = store.claim(pending);
      if (job == null) return;
      if (!gateway.configured()) throw new CommonException(ErrorCode.AI_UNAVAILABLE);
      lease = limits.acquire(job.sellerId());
      // claim의 DB 트랜잭션이 끝난 뒤 호출하므로 AI 응답 대기 중에도 판매 중지 등을 처리할 수 있다.
      String text = policy.validate(gateway.summarize(job.input()), job.input());
      store.complete(job, text);
    } catch (CommonException failure) {
      failed(pending, failure.errorCode().code());
    } catch (RuntimeException failure) {
      LOG.warn(
          "AI summary failed listingId={}, type={}",
          pending.listingId(),
          failure.getClass().getSimpleName());
      failed(pending, "AI_001");
    } finally {
      if (lease != null) limits.release(lease);
    }
  }

  private void failed(Pending pending, String code) {
    try {
      store.fail(pending, code);
    } catch (RuntimeException failure) {
      LOG.warn(
          "AI summary failure state unavailable listingId={}, type={}",
          pending.listingId(),
          failure.getClass().getSimpleName());
    }
  }
}
