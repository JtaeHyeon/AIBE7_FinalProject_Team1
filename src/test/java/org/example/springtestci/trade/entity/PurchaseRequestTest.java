package org.example.springtestci.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;

class PurchaseRequestTest {

  @Test
  void acceptsPendingRequestAsReservedTransaction() {
    PurchaseRequest request =
        PurchaseRequest.restore(1L, 2L, 3L, PurchaseRequestStatus.PENDING, null);

    request.accept();

    assertThat(request.requestStatus()).isEqualTo(PurchaseRequestStatus.ACCEPTED);
    assertThat(request.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
  }

  @Test
  void refusesToProcessFinishedRequestAgain() {
    PurchaseRequest request =
        PurchaseRequest.restore(
            1L, 2L, 3L, PurchaseRequestStatus.ACCEPTED, TransactionStatus.RESERVED);

    assertThatThrownBy(request::accept).isInstanceOf(CommonException.class);
    assertThatThrownBy(request::reject).isInstanceOf(CommonException.class);
    assertThatThrownBy(request::cancelPending).isInstanceOf(CommonException.class);
  }

  @Test
  void cancelsReservedRequestWithImmutableServerMetadata() {
    PurchaseRequest request =
        PurchaseRequest.restore(
            1L,
            2L,
            3L,
            PurchaseRequestStatus.ACCEPTED,
            TransactionStatus.RESERVED,
            4L,
            4L,
            null,
            null,
            null);
    Instant cancelledAt = Instant.parse("2026-10-26T03:00:00Z");

    request.cancelReserved(5L, TradeCancellationReason.CONDITION_MISMATCH, cancelledAt);

    assertThat(request.requestStatus()).isEqualTo(PurchaseRequestStatus.CANCELLED);
    assertThat(request.transactionStatus()).isEqualTo(TransactionStatus.CANCELLED);
    assertThat(request.sellerConfirmedVersion()).isNull();
    assertThat(request.buyerConfirmedVersion()).isNull();
    assertThat(request.cancelledById()).isEqualTo(5L);
    assertThat(request.cancelReason()).isEqualTo(TradeCancellationReason.CONDITION_MISMATCH);
    assertThat(request.cancelledAt()).isEqualTo(cancelledAt);
  }
}
