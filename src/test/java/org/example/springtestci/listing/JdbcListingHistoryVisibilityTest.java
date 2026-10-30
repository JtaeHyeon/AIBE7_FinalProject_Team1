package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.listing.repository.JdbcListingHistoryPrivatizer;
import org.example.springtestci.listing.repository.JdbcListingHistoryPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 별도 visibility 컬럼 대신 공개 판매글에서 파생하는 정식 접근 정책의 DB 검증을 확인한다. */
class JdbcListingHistoryVisibilityTest {
  private SingleConnectionDataSource source;
  private JdbcTemplate jdbc;
  private JdbcListingHistoryPrivatizer privatizer;
  private JdbcListingHistoryPublisher publisher;

  @BeforeEach
  void start() {
    source =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:history_visibility_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute(
        "CREATE TABLE products(id BIGINT PRIMARY KEY,owner_id BIGINT,archived_at TIMESTAMP WITH TIME ZONE)");
    jdbc.execute(
        "CREATE TABLE listings(id BIGINT PRIMARY KEY,product_id BIGINT,seller_id BIGINT,status VARCHAR(20))");
    jdbc.update("INSERT INTO products VALUES(101,1,NULL),(201,2,NULL)");
    jdbc.update("INSERT INTO listings VALUES(11,101,1,'ACTIVE')");
    privatizer = new JdbcListingHistoryPrivatizer(jdbc);
    publisher = new JdbcListingHistoryPublisher(jdbc);
  }

  @AfterEach
  void stop() {
    source.destroy();
  }

  @Test
  void onlyOwnedActiveOrReservedListingsCanBePublicationEvidence() {
    for (String state : List.of("ACTIVE", "RESERVED")) {
      jdbc.update("UPDATE listings SET status=?", state);
      assertDoesNotThrow(() -> publisher.publish(101, 11, 1));
      assertThrows(CommonException.class, () -> publisher.publish(201, 11, 1));
      assertThrows(CommonException.class, () -> publisher.publish(101, 11, 2));
    }
    jdbc.update("UPDATE listings SET status='CANCELLED'");
    assertThrows(CommonException.class, () -> publisher.publish(101, 11, 1));
  }

  @Test
  void closingTheLastListingIsPrivacyEvidenceAndOpenListingsAreRejected() {
    assertThrows(CommonException.class, () -> privatizer.makePrivate(101, 11, 1));
    jdbc.update("UPDATE listings SET status='CANCELLED'");
    assertDoesNotThrow(() -> privatizer.makePrivate(101, 11, 1));
    jdbc.update("INSERT INTO listings VALUES(12,101,1,'RESERVED')");
    assertThrows(CommonException.class, () -> privatizer.makePrivate(101, 11, 1));
  }

  @Test
  void mismatchedIdentityArchivedAndTransferredProductsAreNotPrivacyEvidence() {
    jdbc.update("UPDATE listings SET status='CANCELLED'");
    assertThrows(CommonException.class, () -> privatizer.makePrivate(201, 11, 1));
    assertThrows(CommonException.class, () -> privatizer.makePrivate(101, 11, 2));
    jdbc.update("UPDATE products SET owner_id=2 WHERE id=101");
    assertThrows(CommonException.class, () -> privatizer.makePrivate(101, 11, 1));
    jdbc.update("UPDATE products SET owner_id=1,archived_at=CURRENT_TIMESTAMP WHERE id=101");
    assertThrows(CommonException.class, () -> privatizer.makePrivate(101, 11, 1));
  }
}
