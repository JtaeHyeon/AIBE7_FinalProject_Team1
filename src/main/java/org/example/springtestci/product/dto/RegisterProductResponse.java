package org.example.springtestci.product.dto;

import java.time.LocalDate;
import org.example.springtestci.product.entity.ProductCategory;
import org.example.springtestci.product.service.RegisteredProduct;

public record RegisterProductResponse(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt) {

  public static RegisterProductResponse from(RegisteredProduct product) {
    return new RegisterProductResponse(
        product.id(),
        product.category(),
        product.title(),
        product.manufacturer(),
        product.modelName(),
        product.purchaseDate(),
        product.warrantyExpiresAt());
  }
}
