package org.example.springtestci.document.ai;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 응답을 허용된 제품 사실 항목만 남기도록 정제한다.
 *
 * <p>허용 목록에 없는 키는 버리고, 개인정보로 보이는 값(전화번호·이메일·카드/주민번호 형태)은 해당 항목을 비운다. 모든 항목은 문서에서 확인되지 않으면 {@code
 * null}로 남긴다.
 */
public final class ExtractedFactsSanitizer {

  public static final List<String> FIELDS =
      List.of(
          "productName",
          "modelName",
          "purchaseDate",
          "vendor",
          "price",
          "warrantyMonths",
          "repairDate",
          "repairDescription");

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern PHONE =
      Pattern.compile("(?<!\\d)(?:01[016789]|0\\d{1,2})[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
  private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
  private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");

  private ExtractedFactsSanitizer() {}

  public static Map<String, Object> sanitize(Map<String, Object> raw) {
    Map<String, Object> facts = new LinkedHashMap<>();
    for (String field : FIELDS) {
      facts.put(field, null);
    }
    if (raw == null) {
      return facts;
    }
    facts.put("productName", text(raw.get("productName"), 100));
    facts.put("modelName", text(raw.get("modelName"), 150));
    facts.put("vendor", text(raw.get("vendor"), 200));
    facts.put("repairDescription", text(raw.get("repairDescription"), 2000));
    facts.put("purchaseDate", date(raw.get("purchaseDate")));
    facts.put("repairDate", date(raw.get("repairDate")));
    facts.put("price", nonNegativeLong(raw.get("price")));
    facts.put("warrantyMonths", boundedInt(raw.get("warrantyMonths"), 0, 120));
    return facts;
  }

  /** 개인정보로 보이는 값이 포함되어 있는지. 사용자가 확정할 값을 검증할 때도 쓴다. */
  public static boolean looksPersonal(String value) {
    return EMAIL.matcher(value).find()
        || RRN.matcher(value).find()
        || PHONE.matcher(value).find()
        || CARD.matcher(value).find();
  }

  private static String text(Object value, int maxLength) {
    if (!(value instanceof String raw)) {
      return null;
    }
    String cleaned = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
    if (cleaned.isEmpty() || cleaned.length() > maxLength || looksPersonal(cleaned)) {
      return null;
    }
    return cleaned;
  }

  private static String date(Object value) {
    if (!(value instanceof String raw)) {
      return null;
    }
    try {
      LocalDate parsed = LocalDate.parse(raw.strip());
      LocalDate today = LocalDate.now();
      if (parsed.isAfter(today.plusDays(1)) || parsed.isBefore(LocalDate.of(1990, 1, 1))) {
        return null;
      }
      return parsed.toString();
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private static Long nonNegativeLong(Object value) {
    if (value instanceof Number number) {
      double asDouble = number.doubleValue();
      if (asDouble >= 0 && asDouble == Math.floor(asDouble) && asDouble < 1_000_000_000_000L) {
        return number.longValue();
      }
    }
    return null;
  }

  private static Integer boundedInt(Object value, int min, int max) {
    if (value instanceof Number number) {
      double asDouble = number.doubleValue();
      if (asDouble == Math.floor(asDouble) && asDouble >= min && asDouble <= max) {
        return number.intValue();
      }
    }
    return null;
  }
}
