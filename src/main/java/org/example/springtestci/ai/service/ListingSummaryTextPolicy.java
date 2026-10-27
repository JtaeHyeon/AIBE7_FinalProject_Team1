package org.example.springtestci.ai.service;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 문장 수·평문·길이와 R08의 보조 출력 검증을 함께 적용한다. 의미 검토를 완전히 대체하지 않는다. */
@Component
public class ListingSummaryTextPolicy {
  private static final Pattern MULTIPLE = Pattern.compile("[.!?。！？]\\s+\\S");
  private final AiListingTextPolicy common;

  public ListingSummaryTextPolicy(AiListingTextPolicy common) {
    this.common = common;
  }

  /** 서로 다른 3~5개의 한 문장인지 검사하고, 통과한 문장을 한 단락으로 합친다. */
  public String validate(List<String> values, String input) {
    if (values == null || values.size() < 3 || values.size() > 5) invalid();
    var unique = new HashSet<String>();
    for (String value : values) {
      if (value == null
          || value.isBlank()
          || value.length() > 650
          || value.contains("\n")
          || value.contains("\r")) invalid();
      String sentence = value.strip();
      if (!sentence.matches("(?s).*[.!?。！？]$")
          || MULTIPLE.matcher(sentence).find()
          || !unique.add(sentence)) invalid();
    }
    String text = String.join(" ", values.stream().map(String::strip).toList());
    // 제목·본문 제안과 같은 개인정보·금지 표현·미등록 숫자 검사도 요약에 재사용한다.
    return common
        .validate(new ListingSuggestionGateway.Suggestion("제품 상태 요약", text), input)
        .description();
  }

  private void invalid() {
    throw new CommonException(ErrorCode.AI_INVALID_RESULT);
  }
}
