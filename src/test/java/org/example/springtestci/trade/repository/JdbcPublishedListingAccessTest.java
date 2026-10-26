package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcPublishedListingAccessTest {

  private JdbcTemplate jdbcTemplate;
  private JdbcPublishedListingAccess listingAccess;
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    EmbeddedDatabase database =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("listing-access-" + System.nanoTime())
            .build();
    jdbcTemplate = new JdbcTemplate(database);
    listingAccess = new JdbcPublishedListingAccess(jdbcTemplate);
    transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(database));

    jdbcTemplate.execute(
        "create table products (id bigint primary key, owner_id bigint not null, archived_at timestamp with time zone)");
    jdbcTemplate.execute(
        "create table listings (id bigint primary key, product_id bigint not null, seller_id bigint not null, status varchar(20) not null)");
    jdbcTemplate.update("insert into products (id, owner_id) values (1, 10), (2, 20)");
    jdbcTemplate.update("update products set archived_at = current_timestamp where id = 2");
    jdbcTemplate.update(
        "insert into listings (id, product_id, seller_id, status) values (100, 1, 10, 'ACTIVE'), (200, 2, 20, 'ACTIVE')");
  }

  @Test
  void readsPublishedListingFromProductionTables() {
    PublishedListing listing = listingAccess.findById(100).orElseThrow();

    assertThat(listing)
        .isEqualTo(new PublishedListing(100, 1, 10, 10, PublishedListingStatus.ACTIVE));
  }

  @Test
  void treatsArchivedProductAsCancelledConsistently() {
    assertThat(listingAccess.findById(200).orElseThrow().status())
        .isEqualTo(PublishedListingStatus.CANCELLED);
    assertThat(listingAccess.findStatuses(List.of(100L, 200L, 999L)))
        .isEqualTo(
            Map.of(100L, PublishedListingStatus.ACTIVE, 200L, PublishedListingStatus.CANCELLED));
    assertThat(listingAccess.findStatuses(List.of())).isEmpty();
  }

  @Test
  void locksProductAndListingBeforeReturningCurrentState() {
    PublishedListing listing =
        transactionTemplate.execute(status -> listingAccess.findByIdForUpdate(100).orElseThrow());

    assertThat(listing.currentOwnerId()).isEqualTo(10);
    assertThat(listing.status()).isEqualTo(PublishedListingStatus.ACTIVE);
  }

  @Test
  void reservesOnlyAnActiveListing() {
    listingAccess.reserve(100);

    assertThat(
            jdbcTemplate.queryForObject("select status from listings where id = 100", String.class))
        .isEqualTo("RESERVED");
    assertThatThrownBy(() -> listingAccess.reserve(100))
        .isInstanceOf(CommonException.class)
        .extracting(exception -> ((CommonException) exception).errorCode())
        .isEqualTo(ErrorCode.TRADE_CONFLICT);
  }

  @Test
  void releasesOnlyAReservedListing() {
    listingAccess.reserve(100);

    listingAccess.releaseReservation(100);

    assertThat(
            jdbcTemplate.queryForObject("select status from listings where id = 100", String.class))
        .isEqualTo("ACTIVE");
    assertThatThrownBy(() -> listingAccess.releaseReservation(100))
        .isInstanceOf(CommonException.class)
        .extracting(exception -> ((CommonException) exception).errorCode())
        .isEqualTo(ErrorCode.TRADE_CONFLICT);
  }
}
