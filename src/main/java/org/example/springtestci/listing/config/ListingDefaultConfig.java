package org.example.springtestci.listing.config;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.repository.JdbcListingHistoryPrivatizer;
import org.example.springtestci.listing.repository.JdbcListingHistoryPublisher;
import org.example.springtestci.listing.repository.JdbcListingPublishConstraints;
import org.example.springtestci.listing.service.ListingHistoryPrivatizer;
import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.example.springtestci.listing.service.ListingPublishConstraints;
import org.example.springtestci.listing.service.ListingTradeCanceller;
import org.example.springtestci.trade.repository.JdbcPublishedListingAccess;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** 일반 환경의 실제 게시 검사와 아직 연결되지 않은 이력·거래 포트의 기본 빈을 제공한다. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class ListingDefaultConfig {

  @Bean
  @Profile("!sale-draft-demo")
  @ConditionalOnMissingBean(ListingPublishConstraints.class)
  ListingPublishConstraints defaultListingPublishConstraints(JdbcTemplate jdbc, Clock clock) {
    return new JdbcListingPublishConstraints(jdbc, clock);
  }

  @Bean
  @Profile("!sale-draft-demo")
  @ConditionalOnMissingBean(ListingHistoryPublisher.class)
  ListingHistoryPublisher defaultListingHistoryPublisher(JdbcTemplate jdbc) {
    return new JdbcListingHistoryPublisher(jdbc);
  }

  @Bean
  @Profile("!sale-draft-demo")
  @ConditionalOnMissingBean(ListingTradeCanceller.class)
  ListingTradeCanceller defaultListingTradeCanceller() {
    return command -> {
      throw new CommonException(ErrorCode.LISTING_CANCEL_UNAVAILABLE);
    };
  }

  @Bean
  @Profile("!sale-draft-demo")
  @ConditionalOnMissingBean(ListingHistoryPrivatizer.class)
  ListingHistoryPrivatizer defaultListingHistoryPrivatizer(JdbcTemplate jdbc) {
    return new JdbcListingHistoryPrivatizer(jdbc);
  }

  @Bean
  @ConditionalOnMissingBean(PublishedListingAccess.class)
  PublishedListingAccess defaultPublishedListingAccess(JdbcTemplate jdbc) {
    return new PublishedListingAccess() {
      /** 호출자가 잠근 제품 ID의 RESERVED 판매글을 기존 거래 JDBC 잠금 계약으로 조회한다. */
      @Override
      public Optional<PublishedListing> findReservedByProductIdForUpdate(long productId) {
        return new JdbcPublishedListingAccess(jdbc).findReservedByProductIdForUpdate(productId);
      }

      @Override
      public Optional<PublishedListing> findById(long listingId) {
        return jdbc
            .query(
                "SELECT id, product_id, seller_id, status FROM listings WHERE id = ?",
                (rs, i) ->
                    new PublishedListing(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getLong("seller_id"),
                        rs.getLong("seller_id"),
                        PublishedListingStatus.valueOf(rs.getString("status"))),
                listingId)
            .stream()
            .findFirst();
      }

      @Override
      public Optional<PublishedListing> findByIdForUpdate(long listingId) {
        return jdbc
            .query(
                "SELECT id, product_id, seller_id, status FROM listings WHERE id = ? FOR UPDATE",
                (rs, i) ->
                    new PublishedListing(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getLong("seller_id"),
                        rs.getLong("seller_id"),
                        PublishedListingStatus.valueOf(rs.getString("status"))),
                listingId)
            .stream()
            .findFirst();
      }

      @Override
      public Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds) {
        if (listingIds.isEmpty()) return Map.of();
        String inSql = String.join(",", Collections.nCopies(listingIds.size(), "?"));
        return jdbc.query(
            "SELECT id, status FROM listings WHERE id IN (" + inSql + ")",
            ps -> {
              int idx = 1;
              for (Long id : listingIds) ps.setLong(idx++, id);
            },
            rs -> {
              Map<Long, PublishedListingStatus> map = new HashMap<>();
              while (rs.next()) {
                map.put(rs.getLong("id"), PublishedListingStatus.valueOf(rs.getString("status")));
              }
              return map;
            });
      }

      @Override
      public void reserve(long listingId) {
        jdbc.update(
            "UPDATE listings SET status = 'RESERVED' WHERE id = ? AND status = 'ACTIVE'",
            listingId);
      }

      @Override
      public void releaseReservation(long listingId) {
        jdbc.update(
            "UPDATE listings SET status = 'ACTIVE' WHERE id = ? AND status = 'RESERVED'",
            listingId);
      }

      @Override
      public void sell(long listingId) {
        jdbc.update(
            "UPDATE listings SET status = 'SOLD', active_slot = null WHERE id = ? AND status = 'RESERVED'",
            listingId);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(InternalTransferPendingWriter.class)
  InternalTransferPendingWriter defaultInternalTransferPendingWriter(JdbcTemplate jdbc) {
    return command -> {
      jdbc.update(
          "INSERT INTO ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status, pending_slot, token_hash, expires_at) VALUES (?, ?, ?, ?, 'INTERNAL', 'PENDING', 1, ?, ?)",
          command.productId(),
          command.purchaseRequestId(),
          command.fromUserId(),
          command.toUserId(),
          "internal-" + command.purchaseRequestId(),
          java.sql.Timestamp.from(
              java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS)));
    };
  }
}
