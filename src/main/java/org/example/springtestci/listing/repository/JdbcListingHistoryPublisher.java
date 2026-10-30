package org.example.springtestci.listing.repository;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 별도 공개 컬럼이 없는 정식 스키마에서 실제 열린 판매글을 이력 공개의 근거로 검증한다. */
public class JdbcListingHistoryPublisher implements ListingHistoryPublisher {
  private final JdbcTemplate jdbc;

  public JdbcListingHistoryPublisher(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 제품 잠금 아래 저장된 판매글·현재 소유자·공개 상태가 모두 일치해야 게시를 커밋한다. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(long productId, long listingId, long sellerId) {
    try {
      boolean open =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
          SELECT EXISTS (
            SELECT 1 FROM listings l JOIN products p ON p.id=l.product_id
             WHERE l.id=? AND l.product_id=? AND l.seller_id=? AND p.owner_id=?
               AND p.archived_at IS NULL AND l.status IN ('ACTIVE','RESERVED')
          )
          """,
                  Boolean.class,
                  listingId,
                  productId,
                  sellerId,
                  sellerId));
      if (!open) throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
    } catch (DataAccessException failure) {
      throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
    }
  }
}
