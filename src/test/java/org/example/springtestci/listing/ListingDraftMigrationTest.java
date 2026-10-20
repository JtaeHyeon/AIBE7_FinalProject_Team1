package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ListingDraftMigrationTest {

  @Test
  void migratesExistingDraftContentPhotoOrderAndRepresentativeWithoutLoss() {
    String testUrl = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = testUrl != null && !testUrl.isBlank();
    if (postgres && !testUrl.contains("sale_ai_test")) {
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    }
    String schema =
        postgres ? "draft_upgrade_" + Long.toUnsignedString(System.nanoTime()) : "public";
    String url =
        postgres
            ? testUrl + (testUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema
            : "jdbc:h2:mem:draft_image_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var dataSource =
        new SingleConnectionDataSource(
            url,
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa",
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : "",
            true);
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .locations("classpath:db/migration")
          .target("2026101303")
          .load()
          .migrate();
      var jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          """
          INSERT INTO products (id, category, manufacturer, model_name, title, owner_id,
                                serial_number_fingerprint, serial_number_ciphertext)
          VALUES (101, 'LAPTOP', 'Apple', 'MacBook', '내 제품', 1, 'upgrade-fingerprint', 'test-ciphertext')
          """);
      jdbc.update(
          """
          INSERT INTO product_images (id, product_id, storage_key, content_type, sort_order)
          VALUES (301, 101, 'front.png', 'image/png', 0), (302, 101, 'back.png', 'image/png', 1)
          """);
      jdbc.update(
          """
          INSERT INTO listing_drafts (id, product_id, author_id, title, description, price, condition_grade, version)
          VALUES (10, 101, 1, '작성하던 제목', '작성하던 설명', 1200000, 3, 7)
          """);
      jdbc.update(
          "INSERT INTO listing_draft_images (draft_id, image_id, sort_order) VALUES (10, 302, 0), (10, 301, 1)");

      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      assertEquals(
          "작성하던 제목",
          jdbc.queryForObject("SELECT title FROM listing_drafts WHERE id = 10", String.class));
      assertEquals(
          "작성하던 설명",
          jdbc.queryForObject(
              "SELECT description FROM listing_drafts WHERE id = 10", String.class));
      assertEquals(
          1200000,
          jdbc.queryForObject("SELECT price FROM listing_drafts WHERE id = 10", Integer.class));
      assertEquals(
          3,
          jdbc.queryForObject(
              "SELECT condition_grade FROM listing_drafts WHERE id = 10", Integer.class));
      assertEquals(
          7L, jdbc.queryForObject("SELECT version FROM listing_drafts WHERE id = 10", Long.class));
      assertEquals(
          List.of(302L, 301L),
          jdbc.queryForList(
              "SELECT product_image_id FROM draft_images ORDER BY sort_order", Long.class));
      assertEquals(
          List.of(302L),
          jdbc.queryForList(
              "SELECT product_image_id FROM draft_images WHERE is_primary = TRUE", Long.class));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM information_schema.tables WHERE table_name = 'listing_draft_images'",
              Integer.class));
    } finally {
      if (postgres) new JdbcTemplate(dataSource).execute("DROP SCHEMA " + schema + " CASCADE");
      dataSource.destroy();
    }
  }
}
