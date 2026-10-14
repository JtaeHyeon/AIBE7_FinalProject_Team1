package org.example.springtestci.product.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsBySerialNumberFingerprint(String serialNumberFingerprint);

  List<Product> findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(Long ownerId);

  Optional<Product> findByIdAndOwnerIdAndArchivedAtIsNull(Long id, Long ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select product from Product product where product.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
