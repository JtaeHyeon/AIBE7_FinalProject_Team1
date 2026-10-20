package org.example.springtestci.document.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DocumentExtractionTest {

  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

  private DocumentExtraction queued() {
    return DocumentExtraction.request(1L, 1, 10L, NOW, "v1", "test-model", "receipt-v1");
  }

  private DocumentExtraction extracted() {
    DocumentExtraction extraction = queued();
    extraction.startProcessing();
    extraction.completeExtraction(Map.of("purchaseDate", "2025-01-01"));
    return extraction;
  }

  @Test
  void 요청하면_QUEUED_상태로_생성된다() {
    DocumentExtraction extraction = queued();

    assertEquals(ExtractionStatus.QUEUED, extraction.getStatus());
    assertEquals(1, extraction.getDocumentVersion());
    assertEquals(10L, extraction.getRequestedById());
  }

  @Test
  void 정상_흐름은_QUEUED에서_CONFIRMED까지_진행된다() {
    DocumentExtraction extraction = extracted();

    extraction.confirm(99L, NOW);

    assertEquals(ExtractionStatus.CONFIRMED, extraction.getStatus());
    assertEquals(99L, extraction.getResultEventId());
    assertEquals(NOW, extraction.getFactsConfirmedAt());
  }

  @Test
  void 확정하면_추출_후보값을_비운다() {
    DocumentExtraction extraction = extracted();

    extraction.confirm(null, NOW);

    assertNull(extraction.getExtractedFacts());
    assertNull(extraction.getResultEventId());
  }

  @Test
  void 반려하면_후보값을_비우고_REJECTED가_된다() {
    DocumentExtraction extraction = extracted();

    extraction.reject();

    assertEquals(ExtractionStatus.REJECTED, extraction.getStatus());
    assertNull(extraction.getExtractedFacts());
  }

  @Test
  void 처리_중_실패하면_오류_코드가_기록된다() {
    DocumentExtraction extraction = queued();
    extraction.startProcessing();

    extraction.fail("AI_TIMEOUT");

    assertEquals(ExtractionStatus.FAILED, extraction.getStatus());
    assertEquals("AI_TIMEOUT", extraction.getErrorCode());
  }

  @Test
  void 확정은_두_번_할_수_없다() {
    DocumentExtraction extraction = extracted();
    extraction.confirm(null, NOW);

    CommonException exception =
        assertThrows(CommonException.class, () -> extraction.confirm(null, NOW));

    assertEquals(ErrorCode.INVALID_EXTRACTION_STATE, exception.errorCode());
  }

  @Test
  void 추출_전에는_확정할_수_없다() {
    DocumentExtraction extraction = queued();

    assertThrows(CommonException.class, () -> extraction.confirm(null, NOW));
  }

  @Test
  void 실패한_추출은_다시_처리할_수_없다() {
    DocumentExtraction extraction = queued();
    extraction.fail("AI_ERROR");

    assertThrows(CommonException.class, extraction::startProcessing);
  }
}
