package org.example.springtestci.listing.repository;

import java.util.Optional;
import org.example.springtestci.listing.entity.ListingDraft;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA로 초안과 선택 사진을 저장·조회한다. 제품당 초안 하나를 productId로 찾는다. */
public interface ListingDraftRepository extends JpaRepository<ListingDraft, Long> {
  Optional<ListingDraft> findByProductId(long productId);
}
