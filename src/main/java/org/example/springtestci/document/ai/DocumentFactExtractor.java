package org.example.springtestci.document.ai;

import java.util.Map;
import org.example.springtestci.document.entity.DocumentType;

/** 마스킹이 끝난 사본에서 허용된 제품 사실 후보를 추출한다. 구현체는 외부 AI 서비스를 호출할 수 있다. */
public interface DocumentFactExtractor {

  /** 모델 식별자(추출 기록에 남긴다). */
  String modelName();

  Map<String, Object> extract(byte[] image, String contentType, DocumentType type)
      throws DocumentFactExtractionException;
}
