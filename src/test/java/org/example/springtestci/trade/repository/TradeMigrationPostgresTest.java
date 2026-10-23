package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "P2_TRADE_TEST_ENABLED", matches = "true")
class TradeMigrationPostgresTest {

  @Test
  void freshMigrationCreatesTradeAndOwnershipSchema() {
    withIsolatedSchema(
        (dataSource, jdbcTemplate) -> {
          Flyway flyway = Flyway.configure().dataSource(dataSource).load();

          var result = flyway.migrate();

          assertThat(result.success).isTrue();
          assertThat(
                  jdbcTemplate.queryForObject(
                      "select count(*) from information_schema.tables "
                          + "where table_schema = current_schema() "
                          + "and table_name in ('purchase_requests', 'ownership_transfers')",
                      Long.class))
              .isEqualTo(2L);
          assertThat(
                  jdbcTemplate.queryForList(
                      "select indexname from pg_indexes where schemaname = current_schema() "
                          + "and indexname in ('uq_purchase_requests_pending_room', "
                          + "'uq_purchase_requests_reserved_listing', "
                          + "'uq_ownership_transfers_pending_product')",
                      String.class))
              .containsExactlyInAnyOrder(
                  "uq_purchase_requests_pending_room",
                  "uq_purchase_requests_reserved_listing",
                  "uq_ownership_transfers_pending_product");
          flyway.validate();
          assertThat(flyway.migrate().migrationsExecuted).isZero();
        });
  }

  @Test
  void upgradePreservesLegacyChatIdsAndIsolatesPublishedListingIds() {
    withIsolatedSchema(
        (dataSource, jdbcTemplate) -> {
          Flyway.configure()
              .dataSource(dataSource)
              .target(MigrationVersion.fromVersion("2026102002"))
              .load()
              .migrate();
          jdbcTemplate.update(
              "insert into chat_rooms (listing_id, seller_id, inquirer_id) values (77, 1, 2)");

          Flyway flyway = Flyway.configure().dataSource(dataSource).load();
          flyway.migrate();

          jdbcTemplate.update(
              "insert into users (id, email, password_hash, nickname, role, email_verified, created_at, updated_at) "
                  + "values (1, 'seller@migration.test', 'hash', 'seller', 'USER', true, current_timestamp, current_timestamp)");
          jdbcTemplate.update(
              "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, "
                  + "serial_number_fingerprint, title, owner_id, serial_number_ciphertext) "
                  + "values (1, 'LAPTOP', 'maker', 'MAKER', 'model', 'migration-fingerprint', "
                  + "'title', 1, 'ciphertext')");
          Long listingId =
              jdbcTemplate.queryForObject(
                  "insert into listings (product_id, seller_id, title, price, condition_grade, "
                      + "description, status, published_at, source_draft_id, source_draft_version, active_slot) "
                      + "values (1, 1, 'title', 1, 1, 'description', 'ACTIVE', current_timestamp, 1, 0, 1) "
                      + "returning id",
                  Long.class);

          assertThat(listingId).isGreaterThan(77L);
          assertThat(
                  jdbcTemplate.queryForObject(
                      "select listing_id from chat_rooms where seller_id = 1 and inquirer_id = 2",
                      Long.class))
              .isEqualTo(77L);
          assertThat(
                  jdbcTemplate.queryForObject(
                      "select count(*) from listings where id = 77", Long.class))
              .isZero();
          assertThat(
                  jdbcTemplate.queryForObject(
                      "select count(*) from information_schema.tables "
                          + "where table_schema = current_schema() "
                          + "and table_name in ('purchase_requests', 'ownership_transfers')",
                      Long.class))
              .isEqualTo(2L);
          flyway.validate();
        });
  }

  private void withIsolatedSchema(MigrationAssertion assertion) {
    String url = requiredEnvironment("P2_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P2_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P2_TRADE_TEST_DB_PASSWORD");
    String schema = "p2_trade_migration_" + UUID.randomUUID().toString().replace("-", "");
    DataSource adminDataSource = new DriverManagerDataSource(url, user, password);
    JdbcTemplate admin = new JdbcTemplate(adminDataSource);
    admin.execute("create schema " + schema);
    try {
      DataSource schemaDataSource =
          new DriverManagerDataSource(withCurrentSchema(url, schema), user, password);
      assertion.verify(schemaDataSource, new JdbcTemplate(schemaDataSource));
    } finally {
      admin.execute("drop schema " + schema + " cascade");
    }
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private String withCurrentSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  @FunctionalInterface
  private interface MigrationAssertion {
    void verify(DataSource dataSource, JdbcTemplate jdbcTemplate);
  }
}
