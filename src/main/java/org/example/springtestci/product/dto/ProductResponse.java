package org.example.springtestci.product.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 일련번호는 응답에 포함하지 않는다. */
public record ProductResponse(
    long productId,
    String title,
    String manufacturer,
    String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    Specifications specifications,
    List<Image> images,
    OffsetDateTime createdAt) {

  public record Specifications(
      String cpu,
      String gpu,
      Integer ramGb,
      Integer storageGb,
      String storageType,
      String display,
      Integer batteryHealthPercent,
      String os) {}

  public record Image(long imageId, String url) {}

  public ProductResponse withImages(List<Image> images) {
    return new ProductResponse(
        productId,
        title,
        manufacturer,
        modelName,
        purchaseDate,
        warrantyExpiresAt,
        specifications,
        images,
        createdAt);
  }
}
