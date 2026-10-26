package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingSummaryTextPolicyTest {
  private final ListingSummaryTextPolicy policy =
      new ListingSummaryTextPolicy(new AiListingTextPolicy());
  private final List<String> valid =
      List.of("등록된 메모리는 18GB입니다.", "등록된 저장장치는 512GB입니다.", "전체 핵심 이력을 확인해 주세요.");

  @Test
  void joinsThreeToFivePlainSentences() {
    assertEquals(String.join(" ", valid), policy.validate(valid, "18 512"));
    var five = new ArrayList<>(valid);
    five.add("판매자가 선택한 등급을 함께 확인하세요.");
    five.add("등록된 기록에 근거한 안내입니다.");
    assertTrue(policy.validate(five, "18 512").contains("안내입니다."));
  }

  @Test
  void rejectsWrongSentenceCountsAndDuplicates() {
    assertThrows(CommonException.class, () -> policy.validate(null, ""));
    assertThrows(CommonException.class, () -> policy.validate(valid.subList(0, 2), "18 512"));
    assertThrows(
        CommonException.class, () -> policy.validate(List.of("문장입니다.", "문장입니다.", "문장입니다."), ""));
    var six = new ArrayList<>(valid);
    six.addAll(valid);
    assertThrows(CommonException.class, () -> policy.validate(six, "18 512"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "문장이 두 개입니다. 또 다른 문장입니다.",
        "마침표 없는 문장",
        "줄\n바꿈입니다.",
        "<script>내용</script>입니다.",
        "실사용 2년입니다.",
        "수리 이력 없음.",
        "등록되지 않은 999GB입니다.",
        "연락처는 person@example.com입니다."
      })
  void rejectsMalformedUnsupportedAndSensitiveOutput(String text) {
    assertThrows(
        CommonException.class,
        () -> policy.validate(List.of(valid.get(0), valid.get(1), text), "18 512"));
  }

  @Test
  void boundsPerSentenceAndTotalLength() {
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(List.of(valid.get(0), valid.get(1), "가".repeat(651) + "."), "18 512"));
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(
                List.of(
                    "가".repeat(600) + ".",
                    "나".repeat(600) + ".",
                    "다".repeat(600) + ".",
                    "라".repeat(600) + "."),
                ""));
  }
}
