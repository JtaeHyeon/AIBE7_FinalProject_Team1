package org.example.springtestci.trade.service;

public record PublishedListing(
    long listingId,
    long productId,
    long sellerId,
    long currentOwnerId,
    PublishedListingStatus status) {}
