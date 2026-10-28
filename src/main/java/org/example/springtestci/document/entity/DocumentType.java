package org.example.springtestci.document.entity;

/** 추출 요청 때 사용자가 알려 주는 문서 종류. AI 안내문 선택에만 쓰고 저장하지 않는다. */
public enum DocumentType {
  RECEIPT,
  WARRANTY,
  REPAIR_REPORT,
  OTHER
}
