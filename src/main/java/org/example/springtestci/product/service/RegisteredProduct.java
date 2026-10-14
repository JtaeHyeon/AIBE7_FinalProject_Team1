package org.example.springtestci.product.service;

import java.time.LocalDate;
import org.example.springtestci.product.entity.Product;
import org.example.springtestci.product.entity.ProductCategory;

public record RegisteredProduct(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt) {

  static RegisteredProduct from(Product product) {
    return new RegisteredProduct(
        product.getId(),
        product.getCategory(),
        product.getTitle(),
        product.getManufacturer(),
        product.getModelName(),
        product.getPurchaseDate(),
        product.getWarrantyExpiresAt());
  }
}
