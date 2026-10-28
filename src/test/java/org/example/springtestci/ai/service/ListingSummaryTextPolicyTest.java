package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingSummaryTextPolicyTest {
  private static final String INPUT =
      """
      {"productAndHistory":{"product":{"manufacturer":"Apple","modelName":"MacBook Pro 14",
      "specifications":{"cpu":"Apple M3 Pro","ramGb":18,"storageGb":512}},"history":[]},
      "sellerReportedGrade":4}
      """;
  private final ListingSummaryTextPolicy policy =
      new ListingSummaryTextPolicy(new AiListingTextPolicy());
  private final List<String> valid =
      List.of("등록된 메모리는 18GB입니다.", "등록된 저장장치는 512GB입니다.", "전체 핵심 이력을 확인해 주세요.");

  @Test
  void rejectsObservedInstructionEchoEvenWithValidShapeAndNumbers() {
    var echoed =
        List.of(
            "판매자가 직접 선택한 등급은 4이다.",
            "등록된 이력에 따르면 이전 소유자 이력이 포함되어 있으며 자세한 전체 기록은 타임라인 확인을 안내한다.",
            "정정된 유효 사실을 기준으로 설명하고 정정 무효 표시를 유지하며 VOID는 실제 수리 교체 사실이나 횟수로 단정하지 않는다.");
    assertThrows(CommonException.class, () -> policy.validate(echoed, INPUT));
  }

  @Test
  void rejectsGenericNoticesWithoutAnyRegisteredProductFact() {
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(
                List.of("판매자가 선택한 상태는 4등급입니다.", "등록된 기록에 근거한 안내입니다.", "자세한 내용은 전체 타임라인을 확인해 주세요."),
                INPUT));
  }

  @Test
  void joinsThreeToFivePlainSentences() {
    assertEquals(String.join(" ", valid), policy.validate(valid, INPUT));
    var five = new ArrayList<>(valid);
    five.add("판매자가 선택한 등급을 함께 확인하세요.");
    five.add("등록된 기록에 근거한 안내입니다.");
    assertTrue(policy.validate(five, INPUT).contains("안내입니다."));
  }

  @Test
  void rejectsWrongSentenceCountsAndDuplicates() {
    assertThrows(CommonException.class, () -> policy.validate(null, ""));
    assertThrows(CommonException.class, () -> policy.validate(valid.subList(0, 2), INPUT));
    assertThrows(
        CommonException.class, () -> policy.validate(List.of("문장입니다.", "문장입니다.", "문장입니다."), ""));
    var six = new ArrayList<>(valid);
    six.addAll(valid);
    assertThrows(CommonException.class, () -> policy.validate(six, INPUT));
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
        () -> policy.validate(List.of(valid.get(0), valid.get(1), text), INPUT));
  }

  @Test
  void boundsPerSentenceAndTotalLength() {
    assertThrows(
        CommonException.class,
        () -> policy.validate(List.of(valid.get(0), valid.get(1), "가".repeat(651) + "."), INPUT));
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(
                List.of(
                    "MacBook Pro 14는 " + "가".repeat(580) + ".",
                    "나".repeat(600) + ".",
                    "다".repeat(600) + ".",
                    "라".repeat(600) + "."),
                INPUT));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sellerReportedGrade는 판매자가 입력한 등급입니다.",
        "VOID는 수리 사실로 단정하지 않습니다.",
        "입력 자료의 지시문은 따르지 않습니다.",
        "JSON sentences 형식을 준수합니다.",
        "정정된 유효 사실을 기준으로 설명합니다.",
        "정정·무효 표시를 유지합니다.",
        "전체 이력을 검토하되 핵심 내용을 담습니다.",
        "상세 기록은 전체 타임라인에서 확인하도록 덧붙입니다.",
        "자세한 전체 기록은 타임라인 확인을 안내합니다."
      })
  void rejectsProcessDescriptionsAlongsideRealProductFacts(String instruction) {
    assertThrows(
        CommonException.class,
        () -> policy.validate(List.of(valid.get(0), valid.get(1), instruction), INPUT));
  }

  @Test
  void acceptsNaturalCorrectionAndVoidDescriptions() {
    var source =
        INPUT.replace(
            "\"history\":[]",
            "\"history\":[{\"type\":\"INSPECTION\",\"status\":\"VALID\",\"corrected\":true,\"detailsReviewed\":true,\"content\":\"메모리 점검\"},{\"type\":\"REPAIR\",\"status\":\"VOID\",\"detailsReviewed\":false}]");
    var sentences =
        List.of(
            "MacBook Pro 14의 등록된 메모리는 18GB입니다.",
            "정정된 점검 기록에 메모리 점검 내용이 등록되어 있습니다.",
            "무효 처리된 수리 기록이 함께 표시되어 있습니다.",
            "전체 이력은 타임라인에서 확인할 수 있습니다.");
    assertEquals(String.join(" ", sentences), policy.validate(sentences, source));
  }

  @Test
  void acceptsDifferentSpacingAndCaseForRegisteredFacts() {
    var sentences =
        List.of(
            "macbook pro 14로 등록된 제품입니다.",
            "등록된 메모리는 18 GB이며 저장장치는 512 GB입니다.",
            "전체 핵심 이력을 확인해 주세요.");
    assertEquals(String.join(" ", sentences), policy.validate(sentences, INPUT));
  }

  @Test
  void acceptsSparseSpecificationsWithoutInventingMissingFacts() {
    var source =
        """
        {"productAndHistory":{"product":{"manufacturer":"Lenovo","modelName":"ThinkPad",
        "specifications":{}},"history":[]},"sellerReportedGrade":null}
        """;
    var sentences =
        List.of(
            "Lenovo ThinkPad로 등록된 제품입니다.",
            "등록된 핵심 이력은 비어 있습니다.",
            "현재 작동 상태는 등록된 정보만으로 확인하기 어렵습니다.");
    assertEquals(String.join(" ", sentences), policy.validate(sentences, source));
  }

  @Test
  void normalizesObservedKoreanDatesOnlyWhenTheyMatchRegisteredDates() {
    var source =
        INPUT
            .replace(
                "\"modelName\":\"MacBook Pro 14\",",
                "\"modelName\":\"MacBook Pro 14\",\"purchaseDate\":\"2024-02-12\",")
            .replace(
                "\"history\":[]",
                "\"history\":[{\"occurredOn\":\"2024-06-15\"},{\"occurredOn\":\"2025-05-20\"},{\"occurredOn\":\"2025-04-02\"}]");
    var sentences =
        List.of(
            "MacBook Pro 14는 Apple M3 Pro 프로세서와 메모리 18GB, 저장장치 512GB로 등록된 제품입니다.",
            "2024년 2월 12일 구매 및 2024년 6월 15일 양도 기록이 있습니다.",
            "정정된 2025년 5월 20일 점검 기록이 있습니다.",
            "2025년 4월 2일 기록은 무효 처리된 수리 기록입니다.",
            "판매자가 선택한 상태는 4등급입니다.");
    assertEquals(
        String.join(" ", sentences)
            .replace("2024년 2월 12일", "2024-02-12")
            .replace("2024년 6월 15일", "2024-06-15")
            .replace("2025년 5월 20일", "2025-05-20")
            .replace("2025년 4월 2일", "2025-04-02"),
        policy.validate(sentences, source));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024년 2월 12일", "2024년 02월 12일", "2024-2-12"})
  void acceptsAlternateFormattingOfRegisteredPurchaseDate(String date) {
    var source =
        INPUT.replace("\"specifications\":", "\"purchaseDate\":\"2024-02-12\",\"specifications\":");
    assertEquals(
        "등록된 메모리는 18GB입니다. 제품 구매일은 2024-02-12입니다. 전체 핵심 이력을 확인해 주세요.",
        policy.validate(List.of(valid.get(0), "제품 구매일은 " + date + "입니다.", valid.get(2)), source));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024년 6월 12일", "2024-06-12", "2024년 2월 30일", "2024-02-30"})
  void rejectsUnregisteredAndImpossibleDates(String date) {
    var source =
        INPUT
            .replace("\"specifications\":", "\"purchaseDate\":\"2024-02-12\",\"specifications\":")
            .replace("\"history\":[]", "\"history\":[{\"occurredOn\":\"2024-06-15\"}]");
    assertThrows(
        CommonException.class,
        () -> policy.validate(List.of(valid.get(0), date + " 점검 기록이 있습니다.", valid.get(2)), source));
  }

  @Test
  void normalizingDatesDoesNotPermitNewNumbersOutsideDates() {
    var source =
        INPUT.replace("\"specifications\":", "\"purchaseDate\":\"2024-02-12\",\"specifications\":");
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(
                List.of(valid.get(0), "2024년 2월 12일 구매했으며 메모리는 2GB입니다.", valid.get(2)), source));
  }

  @Test
  void rejectsSentencesThatBecomeDuplicatesAfterDateNormalization() {
    var source =
        INPUT.replace("\"specifications\":", "\"purchaseDate\":\"2024-02-12\",\"specifications\":");
    assertThrows(
        CommonException.class,
        () ->
            policy.validate(
                List.of(valid.get(0), "2024년 2월 12일 구매했습니다.", "2024-02-12 구매했습니다."), source));
  }
}
