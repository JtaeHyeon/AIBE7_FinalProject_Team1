package org.example.springtestci.product.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SerialNumberProtector {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private final SecretKeySpec fingerprintKey;

  public SerialNumberProtector(SerialNumberProperties properties) {
    this.fingerprintKey =
        new SecretKeySpec(
            properties.fingerprintSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  public ProtectedSerialNumber protect(String manufacturer, String rawSerialNumber) {
    String normalizedManufacturer = normalizeManufacturer(manufacturer);
    String normalizedSerialNumber = normalizeSerialNumber(rawSerialNumber);
    return new ProtectedSerialNumber(
        normalizedManufacturer,
        fingerprint(normalizedManufacturer + "\u0000" + normalizedSerialNumber),
        normalizedSerialNumber.substring(Math.max(0, normalizedSerialNumber.length() - 4)));
  }

  String normalizeManufacturer(String manufacturer) {
    if (manufacturer == null) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }

    String normalized = normalizeIdentifier(manufacturer);
    if (normalized.isEmpty() || normalized.length() > 100) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    return normalized;
  }

  String normalizeSerialNumber(String rawSerialNumber) {
    if (rawSerialNumber == null) {
      throw new CommonException(ErrorCode.INVALID_SERIAL_NUMBER);
    }

    String normalized = normalizeIdentifier(rawSerialNumber);
    if (normalized.length() < 4 || normalized.length() > 100) {
      throw new CommonException(ErrorCode.INVALID_SERIAL_NUMBER);
    }
    return normalized;
  }

  private String normalizeIdentifier(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("[\\s-]", "")
        .toUpperCase(Locale.ROOT)
        .trim();
  }

  private String fingerprint(String normalizedSerialNumber) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(fingerprintKey);
      return HexFormat.of()
          .formatHex(mac.doFinal(normalizedSerialNumber.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("시리얼 번호 지문을 생성할 수 없습니다.", exception);
    }
  }
}
