package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ProductImageValidatorTest {

  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
  private static final byte[] WEBP = {
    'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'
  };

  private final ProductImageValidator validator = new ProductImageValidator();

  @Test
  void acceptsPngJpegAndWebp() {
    assertEquals("png", validator.validate("a.PNG", "image/png", PNG).extension());
    assertEquals("jpg", validator.validate("a.jpeg", "image/jpeg", JPEG).extension());
    assertEquals("webp", validator.validate("a.webp", "image/webp", WEBP).extension());
  }

  @Test
  void rejectsAFileWhoseSignatureDoesNotMatchItsExtension() {
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate("a.png", "image/png", JPEG));
  }

  @Test
  void rejectsAWebpWithoutTheWebpMarker() {
    byte[] riffOnly = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE,
        () -> validator.validate("a.webp", "image/webp", riffOnly));
  }

  @Test
  void rejectsADeclaredContentTypeThatDiffersFromTheExtension() {
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate("a.png", "image/jpeg", PNG));
  }

  @Test
  void rejectsUnsupportedExtensionsAndMissingNames() {
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate("a.gif", "image/gif", PNG));
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate("a.pdf", "application/pdf", PNG));
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate("noextension", "image/png", PNG));
    assertCode(ErrorCode.INVALID_PRODUCT_IMAGE, () -> validator.validate(null, "image/png", PNG));
  }

  @Test
  void rejectsAnEmptyFile() {
    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE,
        () -> validator.validate("a.png", "image/png", new byte[0]));
  }

  @Test
  void rejectsAFileOverTheSizeLimit() {
    byte[] large = new byte[(int) ProductImageValidator.MAX_SIZE_BYTES + 1];
    System.arraycopy(PNG, 0, large, 0, PNG.length);

    assertCode(
        ErrorCode.PRODUCT_IMAGE_TOO_LARGE, () -> validator.validate("a.png", "image/png", large));
  }

  private static void assertCode(ErrorCode expected, Runnable action) {
    CommonException exception = assertThrows(CommonException.class, action::run);
    assertEquals(expected, exception.errorCode());
  }
}
