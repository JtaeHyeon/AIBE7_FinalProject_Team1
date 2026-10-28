package org.example.springtestci.history.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import db.migration.V2026102102__isolate_published_listing_ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 합성 신규/demo/옛 일반 DB를 검증하며 팀 DB 적용 사실이나 ownership 업무 매핑을 판정하지 않는다. */
class H40MigrationCompatibilityTest {
  private static final String CURRENT_OWNERSHIP = "V2026102702_1__create_ownership_tables.sql";
  private static final String OLD_OWNERSHIP = "V2026102702__create_ownership_tables.sql";
  @TempDir Path legacyResources;
  private SingleConnectionDataSource source;
  private JdbcTemplate jdbc;
  private String schema;
  private boolean postgres;

  @BeforeEach
  void connectDedicatedDatabase() {
    String url = System.getenv("SALE_DRAFT_TEST_DB_URL");
    postgres = url != null && !url.isBlank();
    if (postgres && !url.contains("sale_ai_test")) {
      throw new IllegalStateException("Use a dedicated sale_ai_test database only");
    }
    schema = postgres ? "h40_migration_" + UUID.randomUUID().toString().replace("-", "") : "public";
    source =
        new SingleConnectionDataSource(
            postgres
                ? url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema
                : "jdbc:h2:mem:h40_"
                    + UUID.randomUUID()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_USER") : "sa",
            postgres ? System.getenv("SALE_DRAFT_TEST_DB_PASSWORD") : "",
            true);
    jdbc = new JdbcTemplate(source);
    if (postgres) jdbc.execute("CREATE SCHEMA " + schema);
  }

  @AfterEach
  void closeDedicatedSchema() {
    if (source != null) {
      if (postgres) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
      source.destroy();
    }
  }

  @Test
  void freshGeneralAndDemoResolveBothVersionsAndKeepRequiredSchemas() {
    candidate("classpath:db/migration", "classpath:db/demo/sale-draft").migrate();
    assertThat(
            jdbc.queryForObject(
                "SELECT script FROM flyway_schema_history WHERE version='2026102702'",
                String.class))
        .isEqualTo("V2026102702__version_listing_card_summary.sql");
    assertThat(
            jdbc.queryForObject(
                "SELECT script FROM flyway_schema_history WHERE version='2026102702.1'",
                String.class))
        .isEqualTo(CURRENT_OWNERSHIP);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='ownership_transfers' AND column_name IN ('token_hash','expires_at','pending_slot')",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='listings' AND column_name='summary_format_version'",
                Integer.class))
        .isEqualTo(1);
    System.out.println(
        "L-03 VERIFIED general+demo fresh " + databaseKind() + " history=" + history());
  }

  /** B의 옛 일반 경로를 정상 적용한 뒤 후보 validate/migrate를 진단하고 데이터·이력을 보존한다. */
  @Test
  void previouslyAppliedGeneralOwnershipIsIncompatibleWithoutChangingDataOrHistory()
      throws Exception {
    for (var resource :
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql")) {
      if (!CURRENT_OWNERSHIP.equals(resource.getFilename())) {
        try (var input = resource.getInputStream()) {
          Files.copy(input, legacyResources.resolve(resource.getFilename()));
        }
      }
    }
    try (var input =
        new ClassPathResource("db/fixtures/h40-legacy-general/" + OLD_OWNERSHIP).getInputStream()) {
      Files.copy(input, legacyResources.resolve(OLD_OWNERSHIP));
    }
    Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .locations("filesystem:" + legacyResources.toAbsolutePath())
        .javaMigrations(new V2026102102__isolate_published_listing_ids())
        .load()
        .migrate();
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,nickname,role,created_at,updated_at) VALUES(1,'legacy-owner@fixture.invalid','unused','owner','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'legacy-buyer@fixture.invalid','unused','buyer','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO products(id,category,manufacturer,manufacturer_normalized,model_name,title,owner_id,serial_number_fingerprint,serial_number_ciphertext) VALUES(1,'LAPTOP','maker','MAKER','model','preserved product',1,'legacy-fingerprint','synthetic')");
    jdbc.update(
        "INSERT INTO ownership_transfers(product_id,from_user_id,to_user_id,token_hash,expires_at,transfer_type,status,pending_slot) VALUES(1,1,2,?,?,'INTERNAL','PENDING',1)",
        "0".repeat(64),
        Timestamp.from(Instant.now().plusSeconds(3600)));
    assertThat(
            jdbc.queryForObject(
                "SELECT script FROM flyway_schema_history WHERE version='2026102702'",
                String.class))
        .isEqualTo(OLD_OWNERSHIP);
    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version='2026102707'",
                Boolean.class))
        .isTrue();
    var beforeHistory = history();
    var beforeRows = jdbc.queryForList("SELECT * FROM ownership_transfers ORDER BY id");
    var beforeProduct = jdbc.queryForMap("SELECT * FROM products WHERE id=1");
    var beforeColumns =
        jdbc.queryForList(
            "SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema=current_schema() ORDER BY table_name,ordinal_position");
    Flyway current = candidate("classpath:db/migration");

    assertThatThrownBy(current::validate)
        .isInstanceOf(FlywayValidateException.class)
        .hasMessageContaining("2026102702")
        .hasMessageContaining("not resolved locally");
    assertThatThrownBy(current::migrate)
        .isInstanceOf(FlywayValidateException.class)
        .hasMessageContaining("2026102702")
        .hasMessageContaining("not resolved locally");

    assertThat(history()).isEqualTo(beforeHistory);
    assertThat(jdbc.queryForList("SELECT * FROM ownership_transfers ORDER BY id"))
        .isEqualTo(beforeRows);
    assertThat(jdbc.queryForMap("SELECT * FROM products WHERE id=1")).isEqualTo(beforeProduct);
    assertThat(
            jdbc.queryForList(
                "SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema=current_schema() ORDER BY table_name,ordinal_position"))
        .isEqualTo(beforeColumns);
    System.out.println(
        "L-05 HARNESS_PASS / UPGRADE_INCOMPATIBLE "
            + databaseKind()
            + ": validate/migrate reject unresolved old 2026102702; data/schema/history unchanged; before="
            + beforeHistory);
  }

  private Flyway candidate(String... locations) {
    return Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .locations(locations)
        .load();
  }

  private List<Map<String, Object>> history() {
    return jdbc.queryForList(
        "SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
  }

  private String databaseKind() {
    return postgres ? "PostgreSQL" : "H2";
  }
}
