package org.example.springtestci.trade.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** P2/P3 거래 계층이 게시 판매글과 제품 소유 관계를 읽고 잠그는 포트다. */
public interface PublishedListingAccess {

  Optional<PublishedListing> findById(long listingId);

  /** 제품을 먼저 잠근 뒤 판매글을 잠가 모든 거래 쓰기의 잠금 순서를 통일한다. */
  Optional<PublishedListing> findByIdForUpdate(long listingId);

  Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds);

  /** ACTIVE 판매글 하나를 RESERVED로 조건부 전환한다. */
  void reserve(long listingId);

  /** RESERVED 판매글 하나만 ACTIVE로 조건부 복구한다. */
  void releaseReservation(long listingId);

  /** RESERVED 판매글 하나를 SOLD로 전환하고 활성 슬롯을 해제한다. */
  void sell(long listingId);
}
