package org.example.springtestci.document.dto;

import java.time.Instant;
import java.util.Map;
import org.example.springtestci.document.entity.DocumentExtraction;
import org.example.springtestci.document.entity.ExtractionStatus;

public record ExtractionResponse(
    Long id,
    Long documentId,
    int documentVersion,
    ExtractionStatus status,
    String schemaVersion,
    Map<String, Object> extractedFacts,
    Long resultEventId,
    String errorCode,
    Instant createdAt,
    Instant updatedAt) {

  public static ExtractionResponse from(DocumentExtraction extraction) {
    return new ExtractionResponse(
        extraction.getId(),
        extraction.getDocumentId(),
        extraction.getDocumentVersion(),
        extraction.getStatus(),
        extraction.getSchemaVersion(),
        extraction.getExtractedFacts(),
        extraction.getResultEventId(),
        extraction.getErrorCode(),
        extraction.getCreatedAt(),
        extraction.getUpdatedAt());
  }
}
