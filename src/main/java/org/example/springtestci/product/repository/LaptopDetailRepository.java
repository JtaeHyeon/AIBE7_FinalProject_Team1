package org.example.springtestci.product.repository;

import java.util.Optional;
import org.example.springtestci.product.entity.LaptopDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaptopDetailRepository extends JpaRepository<LaptopDetail, Long> {

  @Query(
      """
      select detail
      from LaptopDetail detail
      join fetch detail.product product
      where product.id = :productId
        and product.ownerId = :ownerId
        and product.archivedAt is null
      """)
  Optional<LaptopDetail> findOwnedActiveDetail(
      @Param("productId") Long productId, @Param("ownerId") Long ownerId);
}
