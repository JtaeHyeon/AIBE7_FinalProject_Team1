package org.example.springtestci.listing.dto;

import java.time.Instant;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.entity.ListingStatus;

public record PublishedListingResponse(
    long listingId, long productId, ListingStatus status, Instant publishedAt, String url) {
  public static PublishedListingResponse from(Listing listing) {
    return new PublishedListingResponse(
        listing.getId(),
        listing.getProductId(),
        listing.getStatus(),
        listing.getPublishedAt(),
        "/?listingId=" + listing.getId());
  }
}
