package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.listing.service.ListingDetailService;
import org.example.springtestci.product.service.ProductService;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.service.InternalTradeCompletionPort;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.TradeAuditRecorder;
import org.example.springtestci.trade.service.TradeCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class TradeP4FeatureFlagTest {

  private final ApplicationContextRunner controllerRunner =
      new ApplicationContextRunner()
          .withBean(TradeCompletionService.class, () -> mock(TradeCompletionService.class))
          .withBean(CurrentUserIdResolver.class)
          .withUserConfiguration(TradeCompletionController.class);

  @Test
  void featureIsDisabledByDefault() {
    controllerRunner.run(
        context -> assertThat(context).doesNotHaveBean(TradeCompletionController.class));
  }

  @Test
  void controllerCanBeEnabledWhenServiceIsProvided() {
    controllerRunner
        .withPropertyValues("app.features.trade-p4.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TradeCompletionController.class));
  }

  @Test
  void enabledFeatureFailsWithoutCompletionPort() {
    new ApplicationContextRunner()
        .withBean(PurchaseRequestRepository.class, () -> mock(PurchaseRequestRepository.class))
        .withBean(ChatRoomRepository.class, () -> mock(ChatRoomRepository.class))
        .withBean(PublishedListingAccess.class, () -> mock(PublishedListingAccess.class))
        .withBean(ProductService.class, () -> mock(ProductService.class))
        .withBean(ListingDetailService.class, () -> mock(ListingDetailService.class))
        .withBean(ReservationChangeService.class, () -> mock(ReservationChangeService.class))
        .withBean(TradeAuditRecorder.class, () -> mock(TradeAuditRecorder.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withUserConfiguration(MissingCompletionPortConfiguration.class)
        .withPropertyValues(
            "app.features.trade-p4.enabled=true", "app.features.trade-p3.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("InternalTradeCompletionPort");
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MissingCompletionPortConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.features.trade-p4", name = "enabled", havingValue = "true")
    TradeCompletionService tradeCompletionService(
        PurchaseRequestRepository requestRepository,
        ChatRoomRepository roomRepository,
        PublishedListingAccess listingAccess,
        ProductService productService,
        ListingDetailService listingDetailService,
        ReservationChangeService changeService,
        InternalTradeCompletionPort completionPort,
        TradeAuditRecorder auditRecorder,
        Clock clock) {
      return new TradeCompletionService(
          requestRepository,
          roomRepository,
          listingAccess,
          productService,
          listingDetailService,
          changeService,
          completionPort,
          auditRecorder,
          clock);
    }
  }
}
