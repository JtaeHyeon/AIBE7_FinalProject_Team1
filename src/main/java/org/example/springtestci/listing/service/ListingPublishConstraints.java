package org.example.springtestci.listing.service;

/** 거래·이전 기능의 읽기 계약. 제품 행 잠금 안에서 두 상태를 모두 실제 확인해야 한다. */
public interface ListingPublishConstraints {
  /** RESERVED 거래 또는 유효한 PENDING 이전을 확인한다. 원천이 미배포됐으면 ready=false로 게시를 막는다. */
  Snapshot inspect(long productId);

  record Snapshot(boolean ready, boolean ongoingTrade, boolean pendingTransfer) {}
}
