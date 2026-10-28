package org.example.springtestci.history.repository;

import java.time.Instant;
import java.util.List;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductEventRepository extends JpaRepository<ProductEvent, Long> {

  List<ProductEvent> findByProductIdOrderByOccurredOnDescIdDesc(Long productId);

  List<ProductEvent> findByProductIdAndVisibilityOrderByOccurredOnDescIdDesc(
      Long productId, EventVisibility visibility);

  @Modifying
  @Query(
      "UPDATE ProductEvent e SET e.visibility = :visibility, e.updatedAt = :now WHERE e.productId = :productId")
  int updateVisibilityByProductId(
      @Param("productId") Long productId,
      @Param("visibility") EventVisibility visibility,
      @Param("now") Instant now);
}
