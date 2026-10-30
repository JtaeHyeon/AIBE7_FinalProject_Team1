package org.example.springtestci.document.service;

import org.example.springtestci.document.dto.ExtractedFields;

/** 사용자가 확정한 문서 값을 제품 이력으로 남긴다. 이력 도메인 서비스가 생기면 그쪽 구현으로 교체한다. */
public interface ProductEventWriter {

  /**
   * 확정된 값으로 새 이력을 만들고 문서를 그 이력의 증빙으로 연결한다.
   *
   * @return 새로 만든 이력의 ID
   */
  Long recordFromDocument(Long productId, Long documentId, Long userId, ExtractedFields fields);
}
