package org.example.springtestci.ownership.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 전용 DB의 정식 Migration 제약을 확인한다. 거래의 ownership 대역이나 정식 포트 인수 시험은 아니다. */
@EnabledIfEnvironmentVariable(named = "P2_TRADE_TEST_ENABLED", matches = "true")
class OwnershipSchemaPostgresIntegrationTest {
  private static final String SCHEMA =
      "ownership_contract_" + UUID.randomUUID().toString().replace("-", "");
  private static JdbcTemplate admin;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    String url = System.getenv("P2_TRADE_TEST_DB_URL");
    String user = System.getenv("P2_TRADE_TEST_DB_USER");
    String password = System.getenv("P2_TRADE_TEST_DB_PASSWORD");
    admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    admin.execute("create schema " + SCHEMA);
    var source =
        new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA, user, password);
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
  }

  @AfterAll
  static void cleanup() {
    if (admin != null) admin.execute("drop schema " + SCHEMA + " cascade");
  }

  @BeforeEach
  void fixtures() {
    jdbc.execute("truncate table ownership_transfers, products, users restart identity cascade");
    jdbc.update(
        "insert into users (id,email,password_hash,nickname,role,created_at,updated_at) values (1,'owner@schema.invalid','unused','owner','USER',current_timestamp,current_timestamp),(2,'buyer@schema.invalid','unused','buyer','USER',current_timestamp,current_timestamp)");
    jdbc.update(
        "insert into products (id,category,manufacturer,manufacturer_normalized,model_name,serial_number_fingerprint,title,owner_id,serial_number_ciphertext) values (1,'LAPTOP','maker','MAKER','model','schema-fingerprint','title',1,'unused')");
  }

  @Test
  void realSchemaRequiresTokenAndExpiry() {
    assertThatThrownBy(() -> insert(null, "current_timestamp + interval '1 day'", "PENDING", "1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert("synthetic-token", "null", "PENDING", "1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isZero();
  }

  @Test
  void realSchemaEnforcesOnePendingAndReleasesSlotOnCompletion() {
    insert("synthetic-first", "current_timestamp + interval '1 day'", "PENDING", "1");
    assertThatThrownBy(
            () ->
                insert("synthetic-second", "current_timestamp + interval '1 day'", "PENDING", "1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        "update ownership_transfers set status='COMPLETED',pending_slot=null,completed_at=current_timestamp where token_hash='synthetic-first'");
    insert("synthetic-second", "current_timestamp + interval '1 day'", "PENDING", "1");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ownership_transfers where status='PENDING'", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void realSchemaRejectsAcceptedWithoutInventingItsBusinessMapping() {
    assertThatThrownBy(
            () ->
                insert(
                    "synthetic-accepted",
                    "current_timestamp + interval '1 day'",
                    "ACCEPTED",
                    "null"))
        .isInstanceOf(DataIntegrityViolationException.class);
    insert("synthetic-completed", "current_timestamp + interval '1 day'", "COMPLETED", "null");
    assertThat(jdbc.queryForObject("select status from ownership_transfers", String.class))
        .isEqualTo("COMPLETED");
  }

  private void insert(String token, String expiryExpression, String status, String slotExpression) {
    jdbc.update(
        "insert into ownership_transfers (product_id,from_user_id,to_user_id,token_hash,expires_at,status,transfer_type,pending_slot) values (1,1,2,?,"
            + expiryExpression
            + ",?,'INTERNAL',"
            + slotExpression
            + ")",
        token,
        status);
  }
}
