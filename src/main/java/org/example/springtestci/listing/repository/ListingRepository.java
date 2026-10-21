package org.example.springtestci.listing.repository;

import java.util.Collection;
import java.util.Optional;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ListingRepository extends JpaRepository<Listing, Long> {
  Optional<Listing> findBySourceDraftId(long sourceDraftId);

  boolean existsByProductIdAndStatusIn(long productId, Collection<ListingStatus> statuses);

  // 소유권 검사 전에 엔티티를 읽으면 1차 캐시에 변경 전 상태가 남을 수 있어 식별자만 읽는다.
  @Query("select l.productId as productId, l.sellerId as sellerId from Listing l where l.id = :id")
  Optional<Access> findAccessById(long id);

  interface Access {
    long getProductId();

    long getSellerId();
  }
}
