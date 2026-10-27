package org.example.springtestci.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.springtestci.common.audit.BaseTimeEntity;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 문서 하나에 대한 AI 추출 시도 한 번. 재시도할 때마다 새 행이 생기며, 행의 id가 시도 번호다.
 *
 * <p>추출 후보값({@code extractedFacts})은 사용자가 확정하기 전까지만 보관하고 확정하면 비운다.
 */
@Entity
@Table(name = "document_extractions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentExtraction extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long documentId;

  /** 분석에 사용한 사본의 문서 버전. 재마스킹 후 늦게 도착한 결과를 구분한다. */
  @Column(nullable = false, updatable = false)
  private int documentVersion;

  @Column(nullable = false, updatable = false)
  private Long requestedById;

  @Column(nullable = false, updatable = false)
  private Instant consentedAt;

  @Column(nullable = false, length = 30, updatable = false)
  private String consentNoticeVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ExtractionStatus status;

  @Column(nullable = false, length = 100, updatable = false)
  private String modelName;

  @Column(nullable = false, length = 30, updatable = false)
  private String schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> extractedFacts;

  @Column(length = 100)
  private String errorCode;

  private Instant factsConfirmedAt;

  /** 확정으로 새 이력을 만들었을 때만 채운다. */
  private Long resultEventId;

  private DocumentExtraction(
      Long documentId,
      int documentVersion,
      Long requestedById,
      Instant consentedAt,
      String consentNoticeVersion,
      String modelName,
      String schemaVersion) {
    this.documentId = documentId;
    this.documentVersion = documentVersion;
    this.requestedById = requestedById;
    this.consentedAt = consentedAt;
    this.consentNoticeVersion = consentNoticeVersion;
    this.modelName = modelName;
    this.schemaVersion = schemaVersion;
    this.status = ExtractionStatus.QUEUED;
  }

  public static DocumentExtraction request(
      Long documentId,
      int documentVersion,
      Long requestedById,
      Instant consentedAt,
      String consentNoticeVersion,
      String modelName,
      String schemaVersion) {
    return new DocumentExtraction(
        documentId,
        documentVersion,
        requestedById,
        consentedAt,
        consentNoticeVersion,
        modelName,
        schemaVersion);
  }

  public void startProcessing() {
    transitionTo(ExtractionStatus.PROCESSING);
  }

  public void completeExtraction(Map<String, Object> facts) {
    transitionTo(ExtractionStatus.EXTRACTED);
    this.extractedFacts = facts;
  }

  public void fail(String errorCode) {
    transitionTo(ExtractionStatus.FAILED);
    this.errorCode = errorCode;
  }

  /** 사용자가 확정하면 후보값을 비운다. {@code resultEventId}는 새 이력을 만들었을 때만 넘긴다. */
  public void confirm(Long resultEventId, Instant now) {
    transitionTo(ExtractionStatus.CONFIRMED);
    this.extractedFacts = null;
    this.resultEventId = resultEventId;
    this.factsConfirmedAt = now;
  }

  public void reject() {
    transitionTo(ExtractionStatus.REJECTED);
    this.extractedFacts = null;
  }

  private void transitionTo(ExtractionStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new CommonException(ErrorCode.INVALID_EXTRACTION_STATE);
    }
    this.status = next;
  }
}
