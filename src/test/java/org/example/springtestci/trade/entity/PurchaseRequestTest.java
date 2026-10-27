package org.example.springtestci.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
