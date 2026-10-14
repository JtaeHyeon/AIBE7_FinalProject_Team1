package org.example.springtestci.product.dto;

import java.time.LocalDate;
import org.example.springtestci.product.entity.ProductCategory;
import org.example.springtestci.product.service.ProductSummary;

public record ProductSummaryResponse(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    String serialNumberMasked,
    LocalDate purchaseDate) {

  public static ProductSummaryResponse from(ProductSummary product) {
    return new ProductSummaryResponse(
        product.id(),
        product.category(),
        product.title(),
        product.manufacturer(),
        product.modelName(),
        mask(product.serialLast4()),
        product.purchaseDate());
  }

  private static String mask(String serialLast4) {
    return "****" + serialLast4;
  }
}
