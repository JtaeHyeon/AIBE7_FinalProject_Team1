package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 임시 PostgreSQL 스키마에서만 번호 정규화의 조건·롤백·데이터 보존을 검증한다. */
@EnabledIfEnvironmentVariable(named = "SALE_DRAFT_TEST_DB_URL", matches = ".*sale_ai_test.*")
class FlywayVersionAlignmentPostgresTest {
  private String baseUrl, schema, user, password;
  private DriverManagerDataSource source;
  private JdbcTemplate jdbc;
  private final Map<String, List<String>> originalColumns = new LinkedHashMap<>();

  @BeforeEach
  void start() throws Exception {
    baseUrl = System.getenv("SALE_DRAFT_TEST_DB_URL");
    if (!baseUrl.contains("sale_ai_test"))
      throw new IllegalStateException("Dedicated test DB required");
    user = System.getenv("SALE_DRAFT_TEST_DB_USER");
    password = System.getenv("SALE_DRAFT_TEST_DB_PASSWORD");
    schema = "version_alignment_" + Long.toUnsignedString(System.nanoTime());
    try (var connection = DriverManager.getConnection(baseUrl, user, password);
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    source =
        new DriverManagerDataSource(
            baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema,
            user,
            password);
    jdbc = new JdbcTemplate(source);
  }

  @AfterEach
  void stop() throws Exception {
    if (schema == null) return;
    if (!baseUrl.contains("sale_ai_test") || !schema.matches("version_alignment_[0-9]+"))
      throw new IllegalStateException("Unexpected cleanup target");
    try (var connection = DriverManager.getConnection(baseUrl, user, password);
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  @Test
  void aFreshDatabaseHasUniqueVersionsAndOwnershipPendingSlots() {
    flyway(null).migrate();
    flyway(null).validate();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='ownership_transfers' AND column_name='pending_slot'",
            Integer.class,
            schema));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version='2026102804' AND success",
            Integer.class));
  }

  @Test
  void canonicalHistoryNeedsNoChangeAndUpgradesWithoutLosingRows() throws Exception {
    legacy(false);
    var before = businessRows();
    String history = summaryHistory();
    align(databaseName(), schema);
    assertEquals(history, summaryHistory());
    flyway(null).migrate();
    flyway(null).validate();
    assertEquals(before, businessRows());
  }

  @Test
  void renamedHistoryAlignsOnlyVersionAndScriptAndCanBeRunTwice() throws Exception {
    legacy(true);
    var before = businessRows();
    String history = summaryHistory();
    align(databaseName(), schema);
    assertEquals(history, summaryHistory());
    assertEquals(
        "2026102701",
        jdbc.queryForObject(
            "SELECT version FROM flyway_schema_history WHERE checksum=620449880", String.class));
    align(databaseName(), schema);
    flyway(null).migrate();
    flyway(null).validate();
    assertEquals(before, businessRows());
  }

  @Test
  void aDifferentChecksumIsRejectedWithoutChangingHistory() throws Exception {
    legacy(true);
    jdbc.update("UPDATE flyway_schema_history SET checksum=1 WHERE version='2026102706'");
    assertRejected(databaseName(), schema);
  }

  @Test
  void bothVersionsAreRejectedWithoutChangingHistory() throws Exception {
    legacy(true);
    jdbc.update(
        "INSERT INTO flyway_schema_history SELECT installed_rank+100,'2026102701',description,type,'V2026102701__support_listing_summary_refresh.sql',checksum,installed_by,installed_on,execution_time,success FROM flyway_schema_history WHERE version='2026102706'");
    assertRejected(databaseName(), schema);
  }

  @Test
  void aWrongDatabaseIsRejectedWithoutChangingHistory() throws Exception {
    legacy(true);
    assertRejected("a_different_database", schema);
  }

  @Test
  void aWrongSchemaIsRejectedWithoutChangingHistory() throws Exception {
    legacy(true);
    assertRejected(databaseName(), "a_different_schema");
  }

  @Test
  void partialDdlIsRejectedWithoutChangingHistory() throws Exception {
    legacy(true);
    jdbc.execute("ALTER TABLE listings DROP COLUMN summary_available_at CASCADE");
    assertRejected(databaseName(), schema);
  }

  @Test
  void anAlreadyAppliedOldOwnershipVersionRequiresSeparateReview() throws Exception {
    legacy(true);
    jdbc.update(
        "INSERT INTO flyway_schema_history VALUES(100,'2026102702','create ownership tables','SQL','V2026102702__create_ownership_tables.sql',1,'fixture',CURRENT_TIMESTAMP,1,TRUE)");
    assertRejected(databaseName(), schema);
  }

  private Flyway flyway(String target) {
    var config =
        Flyway.configure()
            .dataSource(source)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration");
    if (target != null) config.target(target);
    return config.load();
  }

  private void legacy(boolean renamed) {
    flyway("2026102701").migrate();
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES(1,'alignment-one@fixture.invalid','unused','align-one','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'alignment-two@fixture.invalid','unused','align-two','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    new ResourceDatabasePopulator(new ClassPathResource("db/fixtures/listing-draft-products.sql"))
        .execute(source);
    jdbc.update(
        "INSERT INTO listings(id,product_id,seller_id,title,price,condition_grade,description,status,published_at,source_draft_id,source_draft_version,active_slot,summary_status,summary_text,summary_attempt_id,summary_source_product_version,summary_source_listing_version,summary_updated_at,summary_requested_at,summary_available_at) VALUES(11,101,1,'보존할 제목',1000,3,'보존할 설명','ACTIVE',CURRENT_TIMESTAMP,9001,0,1,'READY','보존할 요약',CAST('00000000-0000-0000-0000-000000000001' AS UUID),0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    if (renamed)
      jdbc.update(
          "UPDATE flyway_schema_history SET version='2026102706',script='V2026102706__support_listing_summary_refresh.sql' WHERE version='2026102701'");
  }

  private Map<String, List<String>> businessRows() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of("users", "products", "laptop_details", "product_images", "listings"))
      result.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(t)::text FROM (SELECT "
                  + String.join(
                      ",",
                      originalColumns.computeIfAbsent(
                          table,
                          name ->
                              jdbc.queryForList(
                                  "SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",
                                  String.class,
                                  schema,
                                  name)))
                  + " FROM "
                  + table
                  + ") t ORDER BY to_jsonb(t)::text",
              String.class));
    return result;
  }

  private String databaseName() {
    return jdbc.queryForObject("SELECT current_database()", String.class);
  }

  private String summaryHistory() {
    return jdbc.queryForObject(
        "SELECT (to_jsonb(t)-'version'-'script')::text FROM flyway_schema_history t WHERE checksum=620449880 OR version='2026102706'",
        String.class);
  }

  private void assertRejected(String expectedDatabase, String expectedSchema) throws Exception {
    var before =
        jdbc.queryForList(
            "SELECT row_to_json(t)::text FROM flyway_schema_history t ORDER BY installed_rank",
            String.class);
    assertThrows(SQLException.class, () -> align(expectedDatabase, expectedSchema));
    assertEquals(
        before,
        jdbc.queryForList(
            "SELECT row_to_json(t)::text FROM flyway_schema_history t ORDER BY installed_rank",
            String.class));
  }

  private void align(String expectedDatabase, String expectedSchema) throws Exception {
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (var statement =
            connection.prepareStatement(
                "SELECT set_config('pastport.expected_database',?,true),set_config('pastport.expected_schema',?,true)")) {
          statement.setString(1, expectedDatabase);
          statement.setString(2, expectedSchema);
          statement.execute();
        }
        try (var statement = connection.createStatement()) {
          statement.execute(Files.readString(Path.of("scripts/db/normalize-summary-version.sql")));
        }
        connection.commit();
      } catch (SQLException error) {
        connection.rollback();
        throw error;
      }
    }
  }
}
