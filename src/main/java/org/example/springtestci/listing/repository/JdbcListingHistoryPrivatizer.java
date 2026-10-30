package org.example.springtestci.listing.repository;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.service.ListingHistoryPrivatizer;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 정식 이력 공개 범위는 열린 판매글에서 파생된다. 종료 상태와 소유권을 검증하고 이력 원문은 보존한다. */
public class JdbcListingHistoryPrivatizer implements ListingHistoryPrivatizer {
  private final JdbcTemplate jdbc;

  public JdbcListingHistoryPrivatizer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 제품 잠금 안에서 내 판매글의 종료와 열린 글 부재를 확인한다. 실패하면 판매중지 전체를 롤백한다. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void makePrivate(long productId, long listingId, long sellerId) {
    try {
      boolean closed =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
          SELECT EXISTS (
            SELECT 1 FROM listings l JOIN products p ON p.id=l.product_id
             WHERE l.id=? AND l.product_id=? AND l.seller_id=? AND p.owner_id=?
               AND p.archived_at IS NULL AND l.status='CANCELLED'
          ) AND NOT EXISTS (
            SELECT 1 FROM listings WHERE product_id=? AND status IN ('ACTIVE','RESERVED')
          )
          """,
                  Boolean.class,
                  listingId,
                  productId,
                  sellerId,
                  sellerId,
                  productId));
      if (!closed) throw new CommonException(ErrorCode.LISTING_CANCEL_UNAVAILABLE);
    } catch (DataAccessException failure) {
      throw new CommonException(ErrorCode.LISTING_CANCEL_UNAVAILABLE);
    }
  }
}
