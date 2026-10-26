package org.example.springtestci.ai.service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 형식·개인정보·명백한 금지 표현을 검사하는 보조 방어. 의미상 정확성은 판매자의 확인도 필요하다. */
@Component
public class AiListingTextPolicy {
  private static final Pattern UNSAFE =
      Pattern.compile(
          "(?i)(https?://|www\\.|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|"
              + "0\\d{1,2}[- .]?\\d{3,4}[- .]?\\d{4}|\\d{6}[- ]?[1-4]\\d{6}|"
              + "\\d[\\d,]*\\s*원|시리얼|serial|증빙|영수증|보증서|계좌|연락처|주소|주문번호|[<>]|"
              + "[\\p{Cc}&&[^\\n\\r\\t]]|[\\u202a-\\u202e\\u2066-\\u2069])");
  private static final Pattern CLAIM =
      Pattern.compile(
          "(?i)(실제\\s*사용|실사용|무고장|무하자|수리\\s*(이력|기록|한\\s*적)?\\s*(없|무)|"
              + "고장\\s*(이력|기록|난\\s*적)?\\s*없|정품\\s*(보장|인증|확실)|"
              + "(품질|안전|성능)\\s*보장|구매\\s*(추천|비추천)|가성비|최저가|"
              + "never\\s*(repaired|broken)|guaranteed|fault[- ]free)");
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

  public void validateInput(String json) {
    if (UNSAFE.matcher(json).find()) throw new CommonException(ErrorCode.AI_SOURCE_UNAVAILABLE);
  }

  public ListingSuggestionGateway.Suggestion validate(
      ListingSuggestionGateway.Suggestion suggestion, String source) {
    if (suggestion == null) invalid();
    String title = suggestion.title() == null ? "" : suggestion.title().strip();
    String description = suggestion.description() == null ? "" : suggestion.description().strip();
    if (title.isBlank()
        || title.length() > 60
        || title.contains("\n")
        || title.contains("\r")
        || description.isBlank()
        || description.length() > 2000) invalid();
    String text = title + "\n" + description;
    if (UNSAFE.matcher(text).find() || CLAIM.matcher(text).find()) invalid();
    Set<String> allowed = numbers(source);
    if (!allowed.containsAll(numbers(text))) invalid();
    return new ListingSuggestionGateway.Suggestion(title, description);
  }

  private Set<String> numbers(String source) {
    Set<String> values = new HashSet<>();
    var matches = NUMBER.matcher(source);
    while (matches.find()) values.add(matches.group());
    return values;
  }

  private void invalid() {
    throw new CommonException(ErrorCode.AI_INVALID_RESULT);
  }
}
