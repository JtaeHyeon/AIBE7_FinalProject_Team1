package org.example.springtestci.product.service;

import java.time.LocalDate;
import org.example.springtestci.product.entity.Product;
import org.example.springtestci.product.entity.ProductCategory;

public record ProductSummary(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    String serialLast4,
    LocalDate purchaseDate) {

  static ProductSummary from(Product product) {
    return new ProductSummary(
        product.getId(),
        product.getCategory(),
        product.getTitle(),
        product.getManufacturer(),
        product.getModelName(),
        product.getSerialLast4(),
        product.getPurchaseDate());
  }
}
