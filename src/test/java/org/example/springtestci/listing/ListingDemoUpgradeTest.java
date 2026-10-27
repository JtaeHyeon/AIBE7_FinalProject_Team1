package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListingDemoUpgradeTest {
  @TempDir Path directory;

  @Test
  void upgradesPersistedDemoAndPublishesWithAnotherConnectionWithoutLosingDraft() throws Exception {
    String url = url();
    migrate(url, "2026102102");
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM users");
      statement.executeUpdate(
          "INSERT INTO listing_drafts(id,product_id,author_id,title,description,price,condition_grade,version) VALUES(10,101,1,'보존할 제목','보존할 설명',1200000,4,3)");
      statement.executeUpdate(
          "INSERT INTO draft_images(draft_id,product_image_id,sort_order,is_primary) VALUES(10,301,0,TRUE)");
    }

    // 이전 파일 DB를 다시 연 뒤 연결 하나를 유지해 실제 커넥션 풀의 업데이트 과정을 재현한다.
    // Flyway가 DDL 연결을 닫은 뒤 다른 연결에서 CHECK(IN ...)을 평가해야 한다.
    try (var keeper = DriverManager.getConnection(url, "sa", "")) {
      migrate(url, null);
      try (var connection = DriverManager.getConnection(url, "sa", "");
          var statement = connection.createStatement()) {
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM users WHERE id IN (1,2)")) {
          rows.next();
          assertEquals(2, rows.getInt(1));
        }
        try (var rows =
            statement.executeQuery(
                "SELECT title,description,version FROM listing_drafts WHERE id=10")) {
          rows.next();
          assertEquals("보존할 제목", rows.getString(1));
          assertEquals("보존할 설명", rows.getString(2));
          assertEquals(3, rows.getLong(3));
        }
        try (var rows =
            statement.executeQuery(
                "SELECT product_image_id FROM draft_images WHERE draft_id=10 AND is_primary=TRUE")) {
          rows.next();
          assertEquals(301, rows.getLong(1));
        }
        statement.executeUpdate(
            "INSERT INTO listings(product_id,seller_id,title,price,condition_grade,description,status,content_version,published_at,source_draft_id,source_draft_version,active_slot) VALUES(101,1,'판매글 제목',1200000,4,'설명','ACTIVE',0,CURRENT_TIMESTAMP,10,3,1)");
        statement.executeUpdate(
            "UPDATE listings SET summary_status='QUEUED',summary_attempt_id=RANDOM_UUID(),summary_source_product_version=1,summary_source_listing_version=0,summary_updated_at=CURRENT_TIMESTAMP");
        statement.executeUpdate("UPDATE listings SET summary_status='READY',summary_text='요약 결과'");
        try (var rows =
            statement.executeQuery("SELECT status,summary_status,summary_text FROM listings")) {
          rows.next();
          assertEquals("ACTIVE", rows.getString(1));
          assertEquals("READY", rows.getString(2));
          assertEquals("요약 결과", rows.getString(3));
        }
        assertThrows(
            SQLException.class,
            () -> statement.executeUpdate("UPDATE listings SET status='INVALID'"));
      }
    }
  }

  @Test
  void restoresOnlyMissingDemoSellerAndPreservesExistingAccount() throws Exception {
    String url = url();
    migrate(url, "2026102102");
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate("UPDATE users SET nickname='유지할이름' WHERE id=1");
      statement.executeUpdate("DELETE FROM users WHERE id=2");
    }
    migrate(url, null);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT id,nickname FROM users ORDER BY id")) {
      rows.next();
      assertEquals(1, rows.getLong(1));
      assertEquals("유지할이름", rows.getString(2));
      rows.next();
      assertEquals(2, rows.getLong(1));
      assertEquals("데모소유자2", rows.getString(2));
    }
  }

  private String url() {
    return "jdbc:h2:file:"
        + directory.resolve("data").toAbsolutePath().toString().replace('\\', '/')
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
  }

  private void migrate(String url, String target) {
    var configuration =
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration", "classpath:db/demo/sale-draft");
    if (target != null) configuration.target(target);
    configuration.load().migrate();
  }
}
