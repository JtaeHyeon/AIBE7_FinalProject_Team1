package org.example.springtestci.document.repository;

import java.util.List;
import org.example.springtestci.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

  List<Document> findByProductIdOrderByIdDesc(Long productId);

  long countByProductId(Long productId);
}
