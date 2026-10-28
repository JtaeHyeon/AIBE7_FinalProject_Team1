package org.example.springtestci.history.service;

import tools.jackson.databind.JsonNode;

/**
 * RESERVED 거래가 있는 제품의 신규 이력을 P3 변경 확인에 같은 트랜잭션으로 전달하는 포트다.
 *
 * <p>구현체는 제품 잠금 뒤 판매글과 구매 요청을 순서대로 잠그고, 저장 실패를 호출 트랜잭션에 전파해야 한다. 기능이 꺼진 환경에는 구현체를 등록하지 않는다.
 */
public interface HistoryChangeReviewRecorder {

  void recordCreated(long productId, long eventId, long factsVersion, JsonNode publicSafeSnapshot);
}
