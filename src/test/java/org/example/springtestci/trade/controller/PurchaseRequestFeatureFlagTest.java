package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PurchaseRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class PurchaseRequestFeatureFlagTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(PurchaseRequestService.class, () -> mock(PurchaseRequestService.class))
          .withBean(CurrentUserIdResolver.class)
          .withUserConfiguration(PurchaseRequestController.class);

  @Test
  void featureIsDisabledByDefault() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(PurchaseRequestController.class));
  }

  @Test
  void featureCanBeEnabledWhenExternalBoundariesAreProvided() {
    contextRunner
        .withPropertyValues("app.features.trade-purchase-requests.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(PurchaseRequestController.class));
  }

  @Test
  void enabledFeatureFailsConfigurationWithoutExternalBoundaries() {
    new ApplicationContextRunner()
        .withBean(PurchaseRequestRepository.class, () -> mock(PurchaseRequestRepository.class))
        .withBean(ChatRoomRepository.class, () -> mock(ChatRoomRepository.class))
        .withUserConfiguration(MissingBoundariesConfiguration.class)
        .withPropertyValues("app.features.trade-purchase-requests.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("PublishedListingAccess");
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MissingBoundariesConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "app.features.trade-purchase-requests",
        name = "enabled",
        havingValue = "true")
    PurchaseRequestService purchaseRequestService(
        PurchaseRequestRepository requestRepository,
        ChatRoomRepository roomRepository,
        PublishedListingAccess listingAccess,
        InternalTransferPendingWriter transferWriter) {
      return new PurchaseRequestService(
          requestRepository, roomRepository, listingAccess, transferWriter);
    }
  }
}
