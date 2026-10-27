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

  /** AI에 보내기 전 공개 자료에 연락처·시리얼 등 금지 정보가 섞여 있는지 보조 검사한다. */
  public void validateInput(String json) {
    if (UNSAFE.matcher(json).find()) throw new CommonException(ErrorCode.AI_SOURCE_UNAVAILABLE);
  }

  /** 생성된 제목·본문의 길이·형식과 금지 표현을 확인한 뒤 화면에서 사용할 텍스트를 반환한다. */
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
    // 입력에 없던 숫자가 추가된 결과를 거부한다. 숫자가 같아도 문장의 의미가 맞는지는 별도 확인이 필요하다.
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
