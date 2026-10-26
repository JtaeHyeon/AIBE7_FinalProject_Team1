package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.service.InternalTransferCancellationPort;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.TradeAuditRecorder;
import org.example.springtestci.trade.service.TradeCancellationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class TradeP3FeatureFlagTest {

  private final ApplicationContextRunner controllerRunner =
      new ApplicationContextRunner()
          .withBean(TradeCancellationService.class, () -> mock(TradeCancellationService.class))
          .withBean(ReservationChangeService.class, () -> mock(ReservationChangeService.class))
          .withBean(CurrentUserIdResolver.class)
          .withUserConfiguration(TradeController.class);

  @Test
  void featureIsDisabledByDefault() {
    controllerRunner.run(context -> assertThat(context).doesNotHaveBean(TradeController.class));
  }

  @Test
  void controllerCanBeEnabledWhenServicesAreProvided() {
    controllerRunner
        .withPropertyValues("app.features.trade-p3.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TradeController.class));
  }

  @Test
  void enabledFeatureFailsWithoutOwnershipCancellationPort() {
    new ApplicationContextRunner()
        .withBean(PurchaseRequestRepository.class, () -> mock(PurchaseRequestRepository.class))
        .withBean(ChatRoomRepository.class, () -> mock(ChatRoomRepository.class))
        .withBean(PublishedListingAccess.class, () -> mock(PublishedListingAccess.class))
        .withBean(TradeAuditRecorder.class, () -> mock(TradeAuditRecorder.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withUserConfiguration(MissingCancellationPortConfiguration.class)
        .withPropertyValues("app.features.trade-p3.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("InternalTransferCancellationPort");
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MissingCancellationPortConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
    TradeCancellationService tradeCancellationService(
        PurchaseRequestRepository requestRepository,
        ChatRoomRepository roomRepository,
        PublishedListingAccess listingAccess,
        InternalTransferCancellationPort transferCancellation,
        TradeAuditRecorder auditRecorder,
        Clock clock) {
      return new TradeCancellationService(
          requestRepository,
          roomRepository,
          listingAccess,
          transferCancellation,
          auditRecorder,
          clock);
    }
  }
}
