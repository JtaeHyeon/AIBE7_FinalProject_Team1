package org.example.springtestci.listing.repository;

import java.util.Optional;
import org.example.springtestci.listing.entity.ListingDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingDraftRepository extends JpaRepository<ListingDraft, Long> {
  Optional<ListingDraft> findByProductId(long productId);
}
