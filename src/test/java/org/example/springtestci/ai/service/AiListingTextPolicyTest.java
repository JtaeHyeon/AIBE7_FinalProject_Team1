package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiListingTextPolicyTest {
  private final AiListingTextPolicy policy = new AiListingTextPolicy();

  @Test
  void allowsRegisteredFactsAndPlainText() {
    var result =
        policy.validate(
            new ListingSuggestionGateway.Suggestion(
                " Apple M3 노트북 ", "메모리 18GB, 저장장치 512GB입니다.\n등록된 이력을 확인해 주세요."),
            "Apple M3 18GB 512GB");
    assertEquals("Apple M3 노트북", result.title());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script>",
        "010-1234-5678",
        "person@example.com",
        "https://example.com",
        "구매금액 100000원",
        "시리얼 ABC",
        "실사용 1년",
        "수리 이력 없음",
        "고장 난 적 없음",
        "정품 보장",
        "구매 추천",
        "가성비",
        "새 메모리 999GB",
        "텍스트\u202e"
      })
  void rejectsSensitiveUnsupportedAndUnsafeClaims(String text) {
    assertThrows(
        CommonException.class,
        () -> policy.validate(new ListingSuggestionGateway.Suggestion("제목", text), "{}"));
  }

  @Test
  void refusesOversizeBlankAndMultilineTitle() {
    for (var value : new String[] {"", " ", "x".repeat(61), "제목\n다음"})
      assertThrows(
          CommonException.class,
          () -> policy.validate(new ListingSuggestionGateway.Suggestion(value, "설명"), "{}"));
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(new ListingSuggestionGateway.Suggestion("제목", "x".repeat(2001)), "{}"));
  }

  @Test
  void preventsSensitiveSourceFromLeavingServer() {
    assertThrows(
        CommonException.class, () -> policy.validateInput("{\"model\":\"person@example.com\"}"));
  }
}
