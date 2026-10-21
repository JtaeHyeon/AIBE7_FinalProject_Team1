package org.example.springtestci.trade.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PublishedListingAccess {

  Optional<PublishedListing> findById(long listingId);

  Optional<PublishedListing> findByIdForUpdate(long listingId);

  Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds);

  void reserve(long listingId);
}
