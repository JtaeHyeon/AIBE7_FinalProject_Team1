package org.example.springtestci.listing.entity;

public enum ListingStatus {
  ACTIVE,
  RESERVED,
  SOLD,
  CANCELLED;

  public boolean isPubliclyReadable() {
    return this == ACTIVE || this == RESERVED;
  }
}
