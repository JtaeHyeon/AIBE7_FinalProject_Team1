package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;

class SerialNumberProtectorTest {

  private final SerialNumberProtector protector =
      new SerialNumberProtector(
          new SerialNumberProperties("test-serial-fingerprint-secret-at-least-32-bytes"));

  @Test
  void equivalentSerialNumbersHaveSameFingerprint() {
    ProtectedSerialNumber first = protector.protect(" Samsung ", " ab-12 34 ");
    ProtectedSerialNumber second = protector.protect("samsung", "AB1234");

    assertEquals(first.fingerprint(), second.fingerprint());
    assertEquals("SAMSUNG", first.normalizedManufacturer());
    assertEquals(first.last4(), second.last4());
    assertEquals("1234", first.last4());
  }

  @Test
  void differentSerialNumbersHaveDifferentFingerprints() {
    assertNotEquals(
        protector.protect("Samsung", "ABCD1234").fingerprint(),
        protector.protect("Samsung", "ABCD5678").fingerprint());
  }

  @Test
  void sameSerialNumberFromDifferentManufacturersHasDifferentFingerprint() {
    assertNotEquals(
        protector.protect("Samsung", "ABCD1234").fingerprint(),
        protector.protect("LG", "ABCD1234").fingerprint());
  }

  @Test
  void rejectsInvalidManufacturerOrSerialNumber() {
    assertThrows(CommonException.class, () -> protector.protect(null, "ABCD1234"));
    assertThrows(CommonException.class, () -> protector.protect(" ", "ABCD1234"));
    assertThrows(CommonException.class, () -> protector.protect("Samsung", null));
    assertThrows(CommonException.class, () -> protector.protect("Samsung", "A-1"));
  }
}
