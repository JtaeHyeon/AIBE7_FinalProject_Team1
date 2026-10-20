package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SerialNumberProtectorTest {

  private static final String SECRET = "unit-test-product-serial-secret-32-bytes!!";

  private final SerialNumberProtector protector = new SerialNumberProtector(SECRET);

  @Test
  void fingerprintIgnoresCaseAndWhitespace() {
    assertEquals(protector.fingerprint("ab12 cd34"), protector.fingerprint("  AB12CD34 "));
  }

  @Test
  void manufacturerNormalizationIgnoresCaseWhitespaceAndHyphens() {
    assertEquals(
        "HEWLETTPACKARD", SerialNumberProtector.normalizeManufacturer(" Hewlett-Packard "));
  }

  @Test
  void fingerprintDiffersForDifferentSerialNumbers() {
    assertNotEquals(protector.fingerprint("AB12CD34"), protector.fingerprint("AB12CD35"));
  }

  @Test
  void fingerprintFitsTheColumnAndHidesTheSerialNumber() {
    String fingerprint = protector.fingerprint("AB12CD34");

    assertEquals(64, fingerprint.length());
    assertFalse(fingerprint.contains("AB12CD34"));
  }

  @Test
  void fingerprintDependsOnTheSecret() {
    SerialNumberProtector other =
        new SerialNumberProtector("another-product-serial-secret-32-bytes!!!");

    assertNotEquals(protector.fingerprint("AB12CD34"), other.fingerprint("AB12CD34"));
  }

  @Test
  void encryptedSerialNumberRoundTripsToTheNormalizedValue() {
    String ciphertext = protector.encrypt("ab12 cd34");

    assertEquals("AB12CD34", protector.decrypt(ciphertext));
  }

  @Test
  void ciphertextIsDifferentEveryTimeAndDoesNotContainThePlaintext() {
    String first = protector.encrypt("AB12CD34");
    String second = protector.encrypt("AB12CD34");

    assertNotEquals(first, second);
    assertFalse(first.contains("AB12CD34"));
  }

  @Test
  void longestSerialNumberStillFitsTheColumn() {
    String ciphertext = protector.encrypt("A".repeat(64));

    assertTrue(ciphertext.length() <= 512);
  }

  @Test
  void ciphertextCannotBeDecryptedWithAnotherSecret() {
    String ciphertext = protector.encrypt("AB12CD34");
    SerialNumberProtector other =
        new SerialNumberProtector("another-product-serial-secret-32-bytes!!!");

    assertThrows(IllegalStateException.class, () -> other.decrypt(ciphertext));
  }

  @Test
  void rejectsASecretShorterThan32Characters() {
    assertThrows(IllegalStateException.class, () -> new SerialNumberProtector("too-short"));
  }
}
