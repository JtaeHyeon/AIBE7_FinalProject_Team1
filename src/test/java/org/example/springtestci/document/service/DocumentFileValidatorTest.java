package org.example.springtestci.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DocumentFileValidatorTest {

  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
  private static final byte[] PDF = "%PDF-1.7".getBytes();

  private final DocumentFileValidator validator = new DocumentFileValidator();

  private ErrorCode errorOf(String filename, String contentType, byte[] content) {
    return assertThrows(
            CommonException.class, () -> validator.validate(filename, contentType, content))
        .errorCode();
  }

  @Test
  void 올바른_PNG_JPG_PDF는_통과한다() {
    assertEquals("png", validator.validate("a.PNG", "image/png", PNG).extension());
    assertEquals("jpg", validator.validate("a.jpeg", "image/jpeg", JPEG).extension());
    assertEquals(
        "application/pdf", validator.validate("a.pdf", "application/pdf", PDF).contentType());
  }

  @Test
  void 빈_파일은_거부한다() {
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("a.pdf", "application/pdf", new byte[0]));
  }

  @Test
  void 허용하지_않는_확장자는_거부한다() {
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("a.exe", "application/pdf", PDF));
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("noext", "application/pdf", PDF));
  }

  @Test
  void 확장자만_속인_파일은_시그니처로_거부한다() {
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("a.pdf", "application/pdf", PNG));
  }

  @Test
  void 선언된_MIME_타입이_다르면_거부한다() {
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("a.png", "application/pdf", PNG));
    assertEquals(ErrorCode.INVALID_DOCUMENT_FILE, errorOf("a.png", null, PNG));
  }

  @Test
  void 열_MB를_넘으면_거부한다() {
    byte[] big = new byte[(int) DocumentFileValidator.MAX_SIZE_BYTES + 1];
    System.arraycopy(PDF, 0, big, 0, PDF.length);

    assertEquals(ErrorCode.DOCUMENT_TOO_LARGE, errorOf("a.pdf", "application/pdf", big));
  }
}
