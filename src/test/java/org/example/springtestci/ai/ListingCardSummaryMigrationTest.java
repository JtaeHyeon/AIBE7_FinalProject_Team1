package org.example.springtestci.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ListingCardSummaryMigrationTest {
  @Test
  void preservesLegacyTextAndEnforcesShortLengthOnlyForCardFormat() {
    verifyUpgrade(false);
  }

  @Test
  void upgradesTheAlreadyAppliedDemoVersionWithoutLosingSummaryOrChecksumHistory() {
    verifyUpgrade(true);
  }

  private void verifyUpgrade(boolean legacyDemo) {
    String testUrl = System.getenv("SALE_DRAFT_TEST_DB_URL");
    boolean postgres = testUrl != null && !testUrl.isBlank();
    if (postgres && !testUrl.contains("sale_ai_test"))
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    String database = legacyDemo ? "card_demo_upgrade" : "card_upgrade";
    String schema = postgres ? database + "_" + Long.toUnsignedString(System.nanoTime()) : "public";
    String[] locations =
        legacyDemo
            ? new String[] {"classpath:db/migration", "classpath:db/demo/sale-draft"}
            : new String[] {"classpath:db/migration"};
    String url =
        postgres
            ? testUrl + (testUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema
            : "jdbc:h2:mem:"
                + database
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var source =
        new SingleConnectionDataSource(
            url,
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa",
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : "",
            true);
    try {
      Flyway.configure()
          .dataSource(source)
          .schemas(schema)
          .defaultSchema(schema)
          .locations(locations)
          .target(legacyDemo ? "2026102702" : "2026102801")
          .load()
          .migrate();
      var jdbc = new JdbcTemplate(source);
      assertEquals(
          legacyDemo ? 1 : 0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='listings' AND column_name='summary_format_version'",
              Integer.class));
      jdbc.update(
          "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES(1001,'card@fixture.invalid','unused','card','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      jdbc.update(
          "INSERT INTO products(id,category,manufacturer,manufacturer_normalized,model_name,title,owner_id,serial_number_fingerprint,serial_number_ciphertext) VALUES(1001,'LAPTOP','Apple','APPLE','MacBook','내 제품',1001,'card-upgrade-fp','fixture')");
      String text = "기존 긴 제품 요약입니다. ".repeat(10);
      jdbc.update(
          "INSERT INTO listings(id,product_id,seller_id,title,price,condition_grade,description,status,published_at,source_draft_id,source_draft_version,active_slot,summary_status,summary_text,summary_attempt_id,summary_source_product_version,summary_source_listing_version,summary_updated_at,summary_requested_at,summary_available_at) VALUES(1,1001,1001,'기존 제목',1000,3,'기존 설명','ACTIVE',CURRENT_TIMESTAMP,1,0,1,'READY',?,CAST('00000000-0000-0000-0000-000000000001' AS UUID),0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          text);
      Flyway.configure()
          .dataSource(source)
          .schemas(schema)
          .defaultSchema(schema)
          .locations(locations)
          .load()
          .migrate();
      assertTrue(
          jdbc.queryForObject(
              "SELECT success FROM flyway_schema_history WHERE version='2026102802'",
              Boolean.class));
      assertEquals(
          text, jdbc.queryForObject("SELECT summary_text FROM listings WHERE id=1", String.class));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT summary_format_version FROM listings WHERE id=1", Integer.class));
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> jdbc.update("UPDATE listings SET summary_format_version=1 WHERE id=1"));
      jdbc.update(
          "UPDATE listings SET summary_text=?,summary_format_version=1 WHERE id=1",
          "MacBook 메모리 모델입니다.");
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT summary_format_version FROM listings WHERE id=1", Integer.class));
      assertEquals(
          "기존 제목", jdbc.queryForObject("SELECT title FROM listings WHERE id=1", String.class));
    } finally {
      if (postgres) new JdbcTemplate(source).execute("DROP SCHEMA " + schema + " CASCADE");
      source.destroy();
    }
  }
}
