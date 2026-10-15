package org.example.springtestci.common.storage;

import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로컬·테스트 환경에서 S3 Presigned URL을 흉내 내는 임시 URL 다운로드 엔드포인트. 서명이 인증을 대신한다. */
@RestController
@Profile({"local", "test"})
public class LocalFileController {

  private final LocalFileStorage storage;

  public LocalFileController(LocalFileStorage storage) {
    this.storage = storage;
  }

  @GetMapping(LocalFileStorage.TEMPORARY_URL_PATH)
  ResponseEntity<Resource> download(
      @RequestParam String key, @RequestParam long expires, @RequestParam String sig)
      throws IOException {
    if (!storage.isValidTemporaryUrl(key, expires, sig)) {
      return ResponseEntity.status(403).build();
    }
    Resource resource = storage.load(key);
    if (!resource.exists()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(mediaTypeOf(key))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-store")
        .body(resource);
  }

  private MediaType mediaTypeOf(String key) {
    String lower = key.toLowerCase();
    if (lower.endsWith(".pdf")) {
      return MediaType.APPLICATION_PDF;
    }
    if (lower.endsWith(".png")) {
      return MediaType.IMAGE_PNG;
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return MediaType.IMAGE_JPEG;
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
