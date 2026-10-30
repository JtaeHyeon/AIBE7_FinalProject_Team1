package org.example.springtestci.document.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractedFactsSanitizerTest {

  @Test
  void keepsOnlyAllowedFieldsAndFillsMissingWithNull() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("productName", "LG gram 17");
    raw.put("customerName", "홍길동");
    raw.put("price", 1_890_000);

    Map<String, Object> facts = ExtractedFactsSanitizer.sanitize(raw);

    assertEquals(ExtractedFactsSanitizer.FIELDS, List.copyOf(facts.keySet()));
    assertEquals("LG gram 17", facts.get("productName"));
    assertEquals(1_890_000L, facts.get("price"));
    assertNull(facts.get("vendor"));
  }

  @Test
  void nullInputGivesAllNullFields() {
    Map<String, Object> facts = ExtractedFactsSanitizer.sanitize(null);

    assertEquals(8, facts.size());
    facts.values().forEach(value -> assertNull(value));
  }

  @Test
  void dropsPersonalLookingValues() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("vendor", "문의 010-1234-5678");
    raw.put("productName", "contact a@b.com");
    raw.put("modelName", "9001011234567");
    raw.put("repairDescription", "카드 1234 5678 9012 3456 결제");

    Map<String, Object> facts = ExtractedFactsSanitizer.sanitize(raw);

    assertNull(facts.get("vendor"));
    assertNull(facts.get("productName"));
    assertNull(facts.get("modelName"));
    assertNull(facts.get("repairDescription"));
  }

  @Test
  void validatesDatesNumbersAndRanges() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("purchaseDate", "2024-03-05");
    raw.put("repairDate", LocalDate.now().plusYears(2).toString());
    raw.put("price", -5);
    raw.put("warrantyMonths", 999);

    Map<String, Object> facts = ExtractedFactsSanitizer.sanitize(raw);

    assertEquals("2024-03-05", facts.get("purchaseDate"));
    assertNull(facts.get("repairDate"));
    assertNull(facts.get("price"));
    assertNull(facts.get("warrantyMonths"));
  }

  @Test
  void rejectsNonIsoDateAndWrongTypes() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("purchaseDate", "2024.03.05");
    raw.put("price", "1000");
    raw.put("productName", 123);

    Map<String, Object> facts = ExtractedFactsSanitizer.sanitize(raw);

    assertNull(facts.get("purchaseDate"));
    assertNull(facts.get("price"));
    assertNull(facts.get("productName"));
  }
}
