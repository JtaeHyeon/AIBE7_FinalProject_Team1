package org.example.springtestci.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

  private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

  @TempDir Path tempDirectory;

  private LocalFileStorage newStorage() {
    return new LocalFileStorage(
        tempDirectory.toString(), "secret", Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Map<String, String> queryOf(String url) {
    return Arrays.stream(URI.create(url).getRawQuery().split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(Collectors.toMap(p -> p[0], p -> p[1]));
  }

  @Test
  void storesLoadsAndDeletesAFile() throws Exception {
    LocalFileStorage storage = newStorage();

    String key =
        storage.store(
            "sample file.txt",
            new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

    assertTrue(key.endsWith("sample_file.txt"));
    assertEquals("content", Files.readString(storage.load(key).getFile().toPath()));

    storage.delete(key);

    assertFalse(storage.load(key).exists());
  }

  @Test
  void rejectsAPathOutsideTheStorageRoot() {
    LocalFileStorage storage = newStorage();

    assertThrows(IllegalArgumentException.class, () -> storage.load("../outside.txt"));
  }

  @Test
  void removesAPartiallyWrittenFileWhenCopyFails() throws Exception {
    LocalFileStorage storage = newStorage();
    InputStream failingContent =
        new InputStream() {
          private int reads;

          @Override
          public int read() throws IOException {
            if (reads++ < 4) {
              return 'a';
            }
            throw new IOException("simulated read failure");
          }
        };

    assertThrows(IOException.class, () -> storage.store("partial.txt", failingContent));

    try (Stream<Path> files = Files.list(tempDirectory)) {
      assertEquals(0L, files.count());
    }
  }

  @Test
  void storesUnderADirectoryWithoutKeepingTheOriginalFilename() throws IOException {
    LocalFileStorage storage = newStorage();

    String key = storage.store("documents/7", "pdf", new ByteArrayInputStream(new byte[] {1}));

    assertTrue(key.matches("documents/7/[0-9a-f-]{36}\\.pdf"));
    assertTrue(storage.load(key).exists());
  }

  @Test
  void acceptsAValidTemporaryUrl() {
    LocalFileStorage storage = newStorage();
    Map<String, String> query =
        queryOf(storage.createTemporaryUrl("documents/7/a.pdf", NOW.plusSeconds(300)));

    assertTrue(
        storage.isValidTemporaryUrl(
            "documents/7/a.pdf", Long.parseLong(query.get("expires")), query.get("sig")));
  }

  @Test
  void rejectsAnExpiredTemporaryUrl() {
    LocalFileStorage storage = newStorage();
    Map<String, String> query = queryOf(storage.createTemporaryUrl("k.pdf", NOW.minusSeconds(1)));

    assertFalse(
        storage.isValidTemporaryUrl(
            "k.pdf", Long.parseLong(query.get("expires")), query.get("sig")));
  }

  @Test
  void rejectsATamperedTemporaryUrl() {
    LocalFileStorage storage = newStorage();
    Map<String, String> query = queryOf(storage.createTemporaryUrl("a.pdf", NOW.plusSeconds(300)));
    long expires = Long.parseLong(query.get("expires"));

    assertFalse(storage.isValidTemporaryUrl("b.pdf", expires, query.get("sig")));
    assertFalse(storage.isValidTemporaryUrl("a.pdf", expires + 1000, query.get("sig")));
    assertFalse(storage.isValidTemporaryUrl("a.pdf", expires, "deadbeef"));
    assertFalse(storage.isValidTemporaryUrl("a.pdf", expires, null));
  }
}
