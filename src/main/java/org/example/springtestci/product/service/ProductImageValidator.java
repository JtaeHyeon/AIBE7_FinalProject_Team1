package org.example.springtestci.product.service;

import java.util.Locale;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 확장자, 선언된 MIME 타입, 파일 시그니처(매직 바이트), 크기를 모두 확인한다. */
@Component
public class ProductImageValidator {

  public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

  public record ImageKind(String extension, String contentType) {}

  public ImageKind validate(String filename, String declaredContentType, byte[] content) {
    if (content == null || content.length == 0) {
      throw new CommonException(ErrorCode.INVALID_PRODUCT_IMAGE);
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw new CommonException(ErrorCode.PRODUCT_IMAGE_TOO_LARGE);
    }
    ImageKind kind = kindOfExtension(filename);
    if (kind == null
        || declaredContentType == null
        || !kind.contentType().equals(declaredContentType.toLowerCase(Locale.ROOT))
        || !matchesSignature(kind.contentType(), content)) {
      throw new CommonException(ErrorCode.INVALID_PRODUCT_IMAGE);
    }
    return kind;
  }

  private ImageKind kindOfExtension(String filename) {
    if (filename == null) {
      return null;
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return null;
    }
    return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
      case "jpg", "jpeg" -> new ImageKind("jpg", "image/jpeg");
      case "png" -> new ImageKind("png", "image/png");
      case "webp" -> new ImageKind("webp", "image/webp");
      default -> null;
    };
  }

  private boolean matchesSignature(String contentType, byte[] content) {
    return switch (contentType) {
      case "image/jpeg" -> startsWith(content, 0, new int[] {0xFF, 0xD8, 0xFF});
      case "image/png" ->
          startsWith(content, 0, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
      // RIFF....WEBP
      case "image/webp" ->
          startsWith(content, 0, new int[] {0x52, 0x49, 0x46, 0x46})
              && startsWith(content, 8, new int[] {0x57, 0x45, 0x42, 0x50});
      default -> false;
    };
  }

  private boolean startsWith(byte[] content, int offset, int[] signature) {
    if (content.length < offset + signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((content[offset + i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
