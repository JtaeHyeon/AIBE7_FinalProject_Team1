package org.example.springtestci.listing.dto;

import java.time.LocalDate;
import java.util.List;

public record DraftProductResponse(
    long productId,
    String title,
    String manufacturer,
    String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    Specifications specifications,
    List<Image> images) {

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
}
