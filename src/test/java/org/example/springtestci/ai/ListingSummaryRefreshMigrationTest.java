package org.example.springtestci.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ListingSummaryRefreshMigrationTest {
  @TempDir Path directory;

  @Test
  void upgradesPersistedReadyAndQueuedSummariesWithoutLosingTheirData() {
    String url =
        "jdbc:h2:file:"
            + directory.resolve("r10-upgrade").toAbsolutePath().toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target("2026102601")
        .load()
        .migrate();
    var dataSource = new DriverManagerDataSource(url, "sa", "");
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES(1,'r10-owner@fixture.invalid','unused','owner','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'r10-other@fixture.invalid','unused','other','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    new ResourceDatabasePopulator(new ClassPathResource("db/fixtures/listing-draft-products.sql"))
        .execute(dataSource);
    jdbc.update(
        "INSERT INTO listings(id,product_id,seller_id,title,description,price,condition_grade,status,content_version,published_at,source_draft_id,source_draft_version,active_slot,summary_status,summary_text,summary_source_product_version,summary_source_listing_version,summary_attempt_id,summary_updated_at) VALUES(1,101,1,'보존할 제목','설명',1200000,4,'ACTIVE',2,CURRENT_TIMESTAMP,100,0,1,'READY','보존할 요약',7,2,RANDOM_UUID(),CURRENT_TIMESTAMP),(2,102,1,'대기 글','설명',1300000,3,'ACTIVE',0,CURRENT_TIMESTAMP,101,0,1,'QUEUED',NULL,0,0,RANDOM_UUID(),CURRENT_TIMESTAMP)");
    var attempt =
        jdbc.queryForObject(
            "SELECT summary_attempt_id FROM listings WHERE id=1", java.util.UUID.class);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE listings SET summary_status='QUEUED' WHERE id=1"));
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    assertEquals(
        "보존할 요약",
        jdbc.queryForObject("SELECT summary_text FROM listings WHERE id=1", String.class));
    assertEquals(
        attempt,
        jdbc.queryForObject(
            "SELECT summary_attempt_id FROM listings WHERE id=1", java.util.UUID.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM listings WHERE summary_requested_at IS NOT NULL AND summary_available_at IS NOT NULL",
            Integer.class));
    jdbc.update("UPDATE listings SET summary_status='QUEUED' WHERE id=1");
    assertEquals(
        "보존할 요약",
        jdbc.queryForObject("SELECT summary_text FROM listings WHERE id=1", String.class));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE listings SET summary_text=' ' WHERE id=1"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE listings SET summary_status='FAILED' WHERE id=1"));
    assertEquals(
        "보존할 제목", jdbc.queryForObject("SELECT title FROM listings WHERE id=1", String.class));
  }
}
