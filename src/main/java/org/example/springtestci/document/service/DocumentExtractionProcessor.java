package org.example.springtestci.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.document.ai.DocumentFactExtractionException;
import org.example.springtestci.document.ai.DocumentFactExtractor;
import org.example.springtestci.document.ai.ExtractedFactsSanitizer;
import org.example.springtestci.document.entity.Document;
import org.example.springtestci.document.entity.DocumentExtraction;
import org.example.springtestci.document.entity.DocumentType;
import org.example.springtestci.document.entity.ExtractionStatus;
import org.example.springtestci.document.repository.DocumentExtractionRepository;
import org.example.springtestci.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 추출 요청 한 건을 처리한다. 외부 AI 호출은 DB 트랜잭션 밖에서 하고, 상태 변경은 짧은 트랜잭션으로 나눠 반영한다.
 *
 * <p>요청 사이에 문서가 교체·삭제되었거나 이미 다른 상태로 바뀐 경우 늦게 도착한 결과는 버린다. 이미지·응답 본문·추출 값은 로그에 남기지 않고, 실패는 분류 코드만
 * 기록한다.
 */
@Component
public class DocumentExtractionProcessor {

  private static final Logger log = LoggerFactory.getLogger(DocumentExtractionProcessor.class);

  private final DocumentExtractionRepository extractionRepository;
  private final DocumentRepository documentRepository;
  private final ObjectProvider<FileStorage> storage;
  private final ObjectProvider<DocumentFactExtractor> extractor;
  private final TransactionTemplate transaction;

  public DocumentExtractionProcessor(
      DocumentExtractionRepository extractionRepository,
      DocumentRepository documentRepository,
      ObjectProvider<FileStorage> storage,
      ObjectProvider<DocumentFactExtractor> extractor,
      PlatformTransactionManager transactionManager) {
    this.extractionRepository = extractionRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
    this.extractor = extractor;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Async("documentExtractionExecutor")
  public void processAsync(Long extractionId, DocumentType type) {
    process(extractionId, type);
  }

  public void process(Long extractionId, DocumentType type) {
    DocumentExtraction started = markProcessing(extractionId);
    if (started == null) {
      return;
    }
    Document document = documentRepository.findById(started.getDocumentId()).orElse(null);
    if (document == null) {
      fail(extractionId, "DOCUMENT_GONE");
      return;
    }
    if (isStale(document, started)) {
      fail(extractionId, "DOCUMENT_CHANGED");
      return;
    }
    FileStorage files = storage.getIfAvailable();
    if (files == null) {
      fail(extractionId, "STORAGE_UNAVAILABLE");
      return;
    }
    DocumentFactExtractor aiExtractor = extractor.getIfAvailable();
    if (aiExtractor == null) {
      fail(extractionId, "AI_UNAVAILABLE");
      return;
    }
    byte[] image;
    try (InputStream input = files.load(document.getObjectKey()).getInputStream()) {
      image = input.readAllBytes();
    } catch (IOException | RuntimeException exception) {
      fail(extractionId, "STORAGE_READ_FAILED");
      return;
    }
    Map<String, Object> facts;
    try {
      facts =
          ExtractedFactsSanitizer.sanitize(
              aiExtractor.extract(image, document.getContentType(), type));
    } catch (DocumentFactExtractionException exception) {
      fail(extractionId, exception.errorCode());
      return;
    } catch (RuntimeException exception) {
      fail(extractionId, "AI_ERROR");
      return;
    }
    complete(extractionId, document.getId(), facts);
  }

  private DocumentExtraction markProcessing(Long extractionId) {
    return transaction.execute(
        status -> {
          DocumentExtraction extraction = extractionRepository.findById(extractionId).orElse(null);
          if (extraction == null || extraction.getStatus() != ExtractionStatus.QUEUED) {
            return null;
          }
          extraction.startProcessing();
          return extractionRepository.save(extraction);
        });
  }

  private boolean isStale(Document document, DocumentExtraction extraction) {
    return document.getVersion() != extraction.getDocumentVersion()
        || document.isRedactionRequired();
  }

  private void complete(Long extractionId, Long documentId, Map<String, Object> facts) {
    transaction.executeWithoutResult(
        status -> {
          DocumentExtraction extraction = extractionRepository.findById(extractionId).orElse(null);
          // 처리 중에 정리 작업이나 문서 삭제로 상태가 바뀌었다면 늦게 온 결과를 버린다.
          if (extraction == null || extraction.getStatus() != ExtractionStatus.PROCESSING) {
            return;
          }
          Document document = documentRepository.findById(documentId).orElse(null);
          if (document == null || isStale(document, extraction)) {
            extraction.fail("DOCUMENT_CHANGED");
          } else {
            extraction.completeExtraction(facts);
          }
          extractionRepository.save(extraction);
        });
  }

  private void fail(Long extractionId, String errorCode) {
    transaction.executeWithoutResult(
        status -> {
          DocumentExtraction extraction = extractionRepository.findById(extractionId).orElse(null);
          if (extraction == null
              || (extraction.getStatus() != ExtractionStatus.PROCESSING
                  && extraction.getStatus() != ExtractionStatus.QUEUED)) {
            return;
          }
          extraction.fail(errorCode);
          extractionRepository.save(extraction);
        });
    log.warn("Document extraction failed extractionId={} errorCode={}", extractionId, errorCode);
  }
}
