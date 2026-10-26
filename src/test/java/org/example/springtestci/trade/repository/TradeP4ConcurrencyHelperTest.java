package org.example.springtestci.trade.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class TradeP4ConcurrencyHelperTest {

  @Test
  void allowsOnlyTradeConflictAsExpectedRaceOutcome() {
    assertThatCode(
            () ->
                TradeP4PostgresIntegrationTest.runIgnoringExpectedConflict(
                    () -> {
                      throw new CommonException(ErrorCode.TRADE_CONFLICT);
                    }))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                TradeP4PostgresIntegrationTest.runIgnoringExpectedConflict(
                    () -> {
                      throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
                    }))
        .isInstanceOfSatisfying(
            CommonException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                    .isEqualTo(ErrorCode.LISTING_HISTORY_UNAVAILABLE));
  }
}
