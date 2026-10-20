package org.example.springtestci.document.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.document.entity.DocumentExtraction;
import org.example.springtestci.document.entity.ExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

  Optional<DocumentExtraction> findByIdAndDocumentId(Long id, Long documentId);

  /** 확정·반려처럼 상태를 바꾸는 요청이 동시에 들어와도 한 번만 처리되도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from DocumentExtraction e where e.id = :id and e.documentId = :documentId")
  Optional<DocumentExtraction> findByIdAndDocumentIdForUpdate(
      @Param("id") Long id, @Param("documentId") Long documentId);

  Optional<DocumentExtraction> findFirstByDocumentIdOrderByIdDesc(Long documentId);

  List<DocumentExtraction> findByDocumentIdOrderByIdDesc(Long documentId);

  boolean existsByDocumentIdAndStatusIn(Long documentId, Collection<ExtractionStatus> statuses);

  /** 서버 재시작 등으로 처리 중에 멈춘 추출을 정리하기 위한 조회. */
  List<DocumentExtraction> findByStatusInAndUpdatedAtBefore(
      Collection<ExtractionStatus> statuses, Instant threshold);

  void deleteByDocumentId(Long documentId);
}
