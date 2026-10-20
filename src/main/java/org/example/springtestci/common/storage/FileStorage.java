package org.example.springtestci.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.springframework.core.io.Resource;

public interface FileStorage {

  String store(String originalFilename, InputStream content) throws IOException;

  /** 원본 파일명을 키에 남기지 않고 {@code directory/UUID.extension} 형태의 키로 저장한다. */
  String store(String directory, String extension, InputStream content) throws IOException;

  Resource load(String key) throws IOException;

  void delete(String key) throws IOException;

  /** {@code expiresAt}까지만 유효한 임시 열람 URL을 만든다. 호출 전에 서버가 접근 권한을 확인해야 한다. */
  String createTemporaryUrl(String key, Instant expiresAt);
}
