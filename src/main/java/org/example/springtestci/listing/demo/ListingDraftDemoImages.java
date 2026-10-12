package org.example.springtestci.listing.demo;

import java.io.IOException;
import org.example.springtestci.common.storage.FileStorage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("sale-draft-demo")
public class ListingDraftDemoImages {
  private final FileStorage storage;
  private final JdbcTemplate jdbc;

  public ListingDraftDemoImages(FileStorage storage, JdbcTemplate jdbc) {
    this.storage = storage;
    this.jdbc = jdbc;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void prepareImages() throws IOException {
    var keys = jdbc.queryForList("SELECT DISTINCT storage_key FROM product_images", String.class);
    for (String key : keys) {
      if (storage.load(key).exists()) continue;
      try (var input =
          new ClassPathResource("static/images/hero-laptop-passport.png").getInputStream()) {
        String stored = storage.store("demo-laptop.png", input);
        jdbc.update("UPDATE product_images SET storage_key = ? WHERE storage_key = ?", stored, key);
      }
    }
  }
}
