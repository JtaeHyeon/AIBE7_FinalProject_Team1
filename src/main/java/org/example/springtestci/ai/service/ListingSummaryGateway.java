package org.example.springtestci.ai.service;

import java.util.List;

/** 공개 제품·전체 핵심 이력으로만 상태 요약을 생성하는 읽기 전용 계약. */
public interface ListingSummaryGateway {
  boolean configured();

  List<String> summarize(String sourceJson);
}
