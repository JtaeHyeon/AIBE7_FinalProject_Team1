package org.example.springtestci.listing.dto;

import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.product.dto.ProductResponse;

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

  /** 판매 화면에 허용된 필드만 복사한다. 소유자용 응답 전체를 외부에 전달하지 않는다. */
  public static DraftProductResponse from(ProductResponse product) {
    var specs = product.specifications();
    return new DraftProductResponse(
        product.productId(),
        product.title(),
        product.manufacturer(),
        product.modelName(),
        product.purchaseDate(),
        product.warrantyExpiresAt(),
        specs == null
            ? null
            : new Specifications(
                specs.cpu(),
                specs.gpu(),
                specs.ramGb(),
                specs.storageGb(),
                specs.storageType(),
                specs.display(),
                specs.batteryHealthPercent(),
                specs.os()),
        product.images().stream()
            .map(
                image ->
                    new Image(
                        image.imageId(),
                        "/api/v1/products/"
                            + product.productId()
                            + "/listing-draft/images/"
                            + image.imageId()))
            .toList());
  }
}
