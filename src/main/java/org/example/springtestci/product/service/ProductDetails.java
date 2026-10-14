package org.example.springtestci.product.service;

import java.time.LocalDate;
import org.example.springtestci.product.entity.LaptopDetail;
import org.example.springtestci.product.entity.Product;
import org.example.springtestci.product.entity.ProductCategory;
import org.example.springtestci.product.entity.StorageType;

public record ProductDetails(
    Long id,
    ProductCategory category,
    String title,
    String manufacturer,
    String modelName,
    String serialLast4,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    String cpuSpec,
    Integer ramGb,
    Integer storageGb,
    StorageType storageType,
    String gpuSpec,
    String displaySpec,
    Short batteryHealthPercent,
    String osName) {

  static ProductDetails from(LaptopDetail detail) {
    Product product = detail.getProduct();
    return new ProductDetails(
        product.getId(),
        product.getCategory(),
        product.getTitle(),
        product.getManufacturer(),
        product.getModelName(),
        product.getSerialLast4(),
        product.getPurchaseDate(),
        product.getWarrantyExpiresAt(),
        detail.getCpuSpec(),
        detail.getRamGb(),
        detail.getStorageGb(),
        detail.getStorageType(),
        detail.getGpuSpec(),
        detail.getDisplaySpec(),
        detail.getBatteryHealthPercent(),
        detail.getOsName());
  }
}
