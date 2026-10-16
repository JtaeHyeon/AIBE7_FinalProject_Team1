package org.example.springtestci.document.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.document.dto.FileUrlResponse;
import org.example.springtestci.document.entity.Document;
import org.example.springtestci.document.repository.DocumentExtractionRepository;
import org.example.springtestci.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 제품 증빙 사본의 업로드·조회·삭제.
 *
 * <p>개인정보가 있는 원본은 브라우저에서만 처리하고, 서버는 마스킹 확인이 끝난 사본만 받는다. 열람과 삭제 권한은 업로더가 아니라 제품의 현재 소유자를 기준으로 한다.
 */
@Service
public class DocumentService {

  static final int MAX_DOCUMENTS_PER_PRODUCT = 20;
  static final Duration FILE_URL_TTL = Duration.ofMinutes(5);

  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

  private final DocumentRepository documentRepository;
  private final DocumentExtractionRepository extractionRepository;
  private final ObjectProvider<FileStorage> fileStorage;
  private final DocumentFileValidator fileValidator;
  private final ProductOwnerReader productOwnerReader;
  private final Clock clock;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentExtractionRepository extractionRepository,
      ObjectProvider<FileStorage> fileStorage,
      DocumentFileValidator fileValidator,
      ProductOwnerReader productOwnerReader,
      Clock clock) {
    this.documentRepository = documentRepository;
    this.extractionRepository = extractionRepository;
    this.fileStorage = fileStorage;
    this.fileValidator = fileValidator;
    this.productOwnerReader = productOwnerReader;
    this.clock = clock;
  }

  /** 현재 소유자가 마스킹을 확인한 사본을 올린다. 업로드 파일명은 확장자 판별에만 쓰고 저장하지 않는다. */
  @Transactional
  public Document upload(
      Long productId,
      Long userId,
      boolean maskingConfirmed,
      String uploadedFilename,
      String contentType,
      byte[] content) {
    if (!maskingConfirmed) {
      throw new CommonException(ErrorCode.MASKING_NOT_CONFIRMED);
    }
    requireCurrentOwner(productId, userId);
    DocumentFileValidator.FileKind kind =
        fileValidator.validate(uploadedFilename, contentType, content);

    if (documentRepository.countByProductId(productId) >= MAX_DOCUMENTS_PER_PRODUCT) {
      throw new CommonException(ErrorCode.DOCUMENT_LIMIT_EXCEEDED);
    }

    String objectKey = store(productId, kind.extension(), content);
    try {
      return documentRepository.save(
          Document.register(
              productId, userId, objectKey, kind.contentType(), content.length, clock.instant()));
    } catch (RuntimeException exception) {
      deleteStoredFile(objectKey);
      throw exception;
    }
  }

  /** 현재 소유자만 조회한다. 소유권이 이전되면 이전 소유자의 요청은 거부된다. */
  @Transactional(readOnly = true)
  public List<Document> list(Long productId, Long userId) {
    requireCurrentOwner(productId, userId);
    return documentRepository.findByProductIdOrderByIdDesc(productId);
  }

  /** 재마스킹이 필요한 사본(개인정보 누락 발견)은 교체되기 전까지 열람할 수 없다. */
  @Transactional(readOnly = true)
  public FileUrlResponse createFileUrl(Long documentId, Long userId) {
    Document document = findDocumentOfCurrentOwner(documentId, userId);
    if (document.isRedactionRequired()) {
      throw new CommonException(ErrorCode.DOCUMENT_REDACTION_REQUIRED);
    }
    Instant expiresAt = clock.instant().plus(FILE_URL_TTL);
    return new FileUrlResponse(
        storage().createTemporaryUrl(document.getObjectKey(), expiresAt), expiresAt);
  }

  /**
   * 현재 소유자가 본인이 올렸고 이력에 연결된 적이 없는(보호되지 않은) 사본만 삭제한다. 이전받은 사본과 이력에 연결된 사본은 삭제할 수 없다. 문서에 딸린 AI 추출
   * 기록도 함께 지운다.
   */
  @Transactional
  public void delete(Long documentId, Long userId) {
    Document document = findDocumentOfCurrentOwner(documentId, userId);
    if (!document.isUploadedBy(userId) || document.isProtected()) {
      throw new CommonException(ErrorCode.DOCUMENT_DELETE_NOT_ALLOWED);
    }
    extractionRepository.deleteByDocumentId(document.getId());
    documentRepository.delete(document);
    deleteStoredFileAfterCommit(document.getObjectKey());
  }

  private Document findDocumentOfCurrentOwner(Long documentId, Long userId) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    Long ownerId = productOwnerReader.findOwnerId(document.getProductId()).orElse(null);
    if (!userId.equals(ownerId)) {
      // 다른 사람의 문서가 존재하는지 알려 주지 않는다.
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return document;
  }

  private void requireCurrentOwner(Long productId, Long userId) {
    Long ownerId =
        productOwnerReader
            .findOwnerId(productId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!ownerId.equals(userId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
  }

  /** 저장소 구현이 없는 환경(운영 프로필 등)에서도 앱은 기동하고, 파일 기능만 503으로 응답한다. */
  private FileStorage storage() {
    FileStorage storage = fileStorage.getIfAvailable();
    if (storage == null) {
      throw new CommonException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE);
    }
    return storage;
  }

  private String store(Long productId, String extension, byte[] content) {
    try {
      return storage()
          .store("documents/" + productId, extension, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      log.error("Failed to store document file productId={}", productId);
      throw new CommonException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private void deleteStoredFileAfterCommit(String objectKey) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deleteStoredFile(objectKey);
            }
          });
    } else {
      deleteStoredFile(objectKey);
    }
  }

  private void deleteStoredFile(String objectKey) {
    try {
      storage().delete(objectKey);
    } catch (IOException | RuntimeException exception) {
      log.warn("Failed to delete stored document file");
    }
  }
}
