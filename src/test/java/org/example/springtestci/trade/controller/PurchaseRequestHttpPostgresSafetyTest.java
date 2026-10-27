package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PurchaseRequestHttpPostgresSafetyTest {

  @Test
  void allowsRedisFlushOnlyWithExplicitPermission() {
    assertThatCode(
            () -> PurchaseRequestHttpPostgresTest.requireDedicatedRedisFlushPermission("true"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsRedisFlushWithoutExplicitPermission() {
    assertThatThrownBy(
            () -> PurchaseRequestHttpPostgresTest.requireDedicatedRedisFlushPermission(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true");
    assertThatThrownBy(
            () -> PurchaseRequestHttpPostgresTest.requireDedicatedRedisFlushPermission("false"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dedicated disposable Redis instance");
  }
}
