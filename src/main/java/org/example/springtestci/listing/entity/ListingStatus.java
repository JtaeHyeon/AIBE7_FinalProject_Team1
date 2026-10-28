package org.example.springtestci.listing.entity;

/** 판매 중·예약 중·판매 완료·판매 중지를 구분한다. 종료된 글은 기록을 남기되 공개 조회를 막는다. */
public enum ListingStatus {
  ACTIVE,
  RESERVED,
  SOLD,
  CANCELLED;

  public boolean isPubliclyReadable() {
    return this == ACTIVE || this == RESERVED;
  }
}
