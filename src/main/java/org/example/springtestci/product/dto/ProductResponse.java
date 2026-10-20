package org.example.springtestci.product.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 일련번호는 현재 소유자의 상세 응답에만 채우고 목록 응답에서는 null로 둔다. */
public record ProductResponse(
    long productId,
    String title,
    String manufacturer,
    String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    String warrantyTerms,
    long factsVersion,
    String serialNumber,
    String warrantyStatus,
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

  public record Image(long imageId, String url, boolean primary, int sortOrder, long byteSize) {}

  public ProductResponse withImages(List<Image> images) {
    return new ProductResponse(
        productId,
        title,
        manufacturer,
        modelName,
        purchaseDate,
        warrantyExpiresAt,
        warrantyTerms,
        factsVersion,
        serialNumber,
        warrantyStatus,
        specifications,
        images,
        createdAt);
  }

  public ProductResponse withSerialNumber(String serialNumber) {
    return new ProductResponse(
        productId,
        title,
        manufacturer,
        modelName,
        purchaseDate,
        warrantyExpiresAt,
        warrantyTerms,
        factsVersion,
        serialNumber,
        warrantyStatus,
        specifications,
        images,
        createdAt);
  }
}
