package org.example.springtestci.document.service;

import java.util.Locale;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 확장자, 선언된 MIME 타입, 파일 시그니처(매직 바이트), 크기를 모두 확인한다. */
@Component
public class DocumentFileValidator {

  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  public record FileKind(String extension, String contentType) {}

  public FileKind validate(String filename, String declaredContentType, byte[] content) {
    if (content == null || content.length == 0) {
      throw new CommonException(ErrorCode.INVALID_DOCUMENT_FILE);
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw new CommonException(ErrorCode.DOCUMENT_TOO_LARGE);
    }

    FileKind byExtension = kindOfExtension(filename);
    if (byExtension == null
        || declaredContentType == null
        || !byExtension.contentType().equals(declaredContentType.toLowerCase(Locale.ROOT))
        || !matchesSignature(byExtension.contentType(), content)) {
      throw new CommonException(ErrorCode.INVALID_DOCUMENT_FILE);
    }
    return byExtension;
  }

  private FileKind kindOfExtension(String filename) {
    if (filename == null) {
      return null;
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return null;
    }
    return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
      case "jpg", "jpeg" -> new FileKind("jpg", "image/jpeg");
      case "png" -> new FileKind("png", "image/png");
      case "pdf" -> new FileKind("pdf", "application/pdf");
      default -> null;
    };
  }

  private boolean matchesSignature(String contentType, byte[] content) {
    return switch (contentType) {
      case "image/jpeg" -> startsWith(content, new int[] {0xFF, 0xD8, 0xFF});
      case "image/png" ->
          startsWith(content, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
      case "application/pdf" -> startsWith(content, new int[] {0x25, 0x50, 0x44, 0x46, 0x2D});
      default -> false;
    };
  }

  private boolean startsWith(byte[] content, int[] signature) {
    if (content.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((content[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
