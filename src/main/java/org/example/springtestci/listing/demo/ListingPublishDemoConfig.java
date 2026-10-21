package org.example.springtestci.listing.demo;

import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.example.springtestci.listing.service.ListingPublishConstraints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** 별도 로컬 샘플 DB에서만 게시 성공 흐름을 제공한다. 실제 거래·이전 모듈 확인을 대신하지 않는다. */
@Configuration(proxyBeanMethods = false)
@Profile("sale-draft-demo")
public class ListingPublishDemoConfig {
  @Bean
  ListingPublishConstraints listingPublishDemoConstraints() {
    return productId -> new ListingPublishConstraints.Snapshot(true, false, false);
  }

  @Bean
  ListingHistoryPublisher listingHistoryDemoPublisher(JdbcTemplate jdbc) {
    return (productId, listingId, sellerId) ->
        jdbc.update(
            "UPDATE product_events SET visibility = 'PUBLIC' WHERE product_id = ?", productId);
  }
}
