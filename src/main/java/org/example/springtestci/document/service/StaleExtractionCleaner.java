package org.example.springtestci.document.service;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import org.example.springtestci.document.entity.DocumentExtraction;
import org.example.springtestci.document.entity.ExtractionStatus;
import org.example.springtestci.document.repository.DocumentExtractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 서버 재시작이나 오류로 처리 중에 멈춘 추출을 실패로 정리한다. 정리된 문서는 사용자가 새 요청으로 다시 시도할 수 있다. */
@Component
public class StaleExtractionCleaner {

  private static final Logger log = LoggerFactory.getLogger(StaleExtractionCleaner.class);

  private final DocumentExtractionRepository extractionRepository;
  private final Clock clock;
  private final Duration staleAfter;

  public StaleExtractionCleaner(
      DocumentExtractionRepository extractionRepository,
      Clock clock,
      @Value("${app.ai.stale-minutes:5}") long staleMinutes) {
    this.extractionRepository = extractionRepository;
    this.clock = clock;
    this.staleAfter = Duration.ofMinutes(Math.max(1, staleMinutes));
  }

  @Scheduled(fixedDelayString = "${app.ai.stale-check-interval-ms:60000}")
  @Transactional
  public int failStale() {
    List<DocumentExtraction> stale =
        extractionRepository.findByStatusInAndUpdatedAtBefore(
            EnumSet.of(ExtractionStatus.QUEUED, ExtractionStatus.PROCESSING),
            clock.instant().minus(staleAfter));
    stale.forEach(extraction -> extraction.fail("AI_TIMEOUT"));
    if (!stale.isEmpty()) {
      log.warn("Failed {} stale document extractions", stale.size());
    }
    return stale.size();
  }
}
