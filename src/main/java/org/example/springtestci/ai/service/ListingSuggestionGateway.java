package org.example.springtestci.ai.service;

/** 읽기 전용 AI 연결 계약. 입력은 서버가 구성한 공개 허용 자료이며 쓰기 도구·대화·문서를 받지 않는다. */
public interface ListingSuggestionGateway {
  boolean configured();

  Suggestion generate(String sourceJson);

  record Suggestion(String title, String description) {}
}
