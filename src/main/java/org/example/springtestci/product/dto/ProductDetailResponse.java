package org.example.springtestci.product.dto;

import java.time.LocalDate;
import org.example.springtestci.product.entity.ProductCategory;
import org.example.springtestci.product.entity.StorageType;
import org.example.springtestci.product.service.ProductDetails;

public record ProductDetailResponse(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    String serialNumberMasked,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    LaptopSpecification laptop) {

  public static ProductDetailResponse from(ProductDetails product) {
    return new ProductDetailResponse(
        product.id(),
        product.category(),
        product.title(),
        product.manufacturer(),
        product.modelName(),
        "****" + product.serialLast4(),
        product.purchaseDate(),
        product.warrantyExpiresAt(),
        new LaptopSpecification(
            product.cpuSpec(),
            product.ramGb(),
            product.storageGb(),
            product.storageType(),
            product.gpuSpec(),
            product.displaySpec(),
            product.batteryHealthPercent(),
            product.osName()));
  }

  public record LaptopSpecification(
      String cpuSpec,
      Integer ramGb,
      Integer storageGb,
      StorageType storageType,
      String gpuSpec,
      String displaySpec,
      Short batteryHealthPercent,
      String osName) {}
}
