package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcPurchaseRequestCompletionTest {

  private JdbcTemplate jdbcTemplate;
  private JdbcPurchaseRequestRepository repository;

  @BeforeEach
  void setUp() {
    EmbeddedDatabase database =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("purchase-completion-" + System.nanoTime())
            .build();
    jdbcTemplate = new JdbcTemplate(database);
    repository = new JdbcPurchaseRequestRepository(jdbcTemplate);
    jdbcTemplate.execute(
        "create table purchase_requests (id bigint primary key, room_id bigint not null, listing_id bigint not null, request_status varchar(20) not null, transaction_status varchar(20), seller_confirmed_version bigint, buyer_confirmed_version bigint, cancelled_by_id bigint, cancel_reason varchar(50), cancelled_at timestamp with time zone)");
    jdbcTemplate.update(
        "insert into purchase_requests (id, room_id, listing_id, request_status, transaction_status) values (1, 10, 20, 'ACCEPTED', 'RESERVED'), (2, 11, 20, 'PENDING', null), (3, 12, 20, 'CANCELLED', null)");
  }

  @Test
  void storesBothConfirmationsAndCompletesExistingColumns() {
    repository.confirmSeller(1L, 200L);
    repository.confirmBuyer(1L, 200L);
    repository.complete(1L, 200L);

    var request = repository.findById(1L).orElseThrow();
    assertThat(request.sellerConfirmedVersion()).isEqualTo(200L);
    assertThat(request.buyerConfirmedVersion()).isEqualTo(200L);
    assertThat(request.transactionStatus()).isEqualTo(TransactionStatus.COMPLETED);
  }

  @Test
  void completionRequiresBothMatchingStoredVersions() {
    repository.confirmSeller(1L, 200L);
    repository.confirmBuyer(1L, 199L);

    assertThatThrownBy(() -> repository.complete(1L, 200L))
        .isInstanceOf(CommonException.class)
        .extracting(exception -> ((CommonException) exception).errorCode())
        .isEqualTo(ErrorCode.TRADE_CONFLICT);
  }

  @Test
  void rejectsOnlyOtherPendingRequestsForSameListing() {
    int updated = repository.rejectPendingByListingId(20L, 1L);

    assertThat(updated).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select request_status from purchase_requests where id = 2", String.class))
        .isEqualTo("REJECTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "select request_status from purchase_requests where id = 3", String.class))
        .isEqualTo("CANCELLED");
  }
}
