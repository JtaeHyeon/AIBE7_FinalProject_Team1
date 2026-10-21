package org.example.springtestci.listing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 게시 시 초안에서 복사한 사진 선택. 제품 자체의 사진 순서는 바꾸지 않는다. */
@Embeddable
public class ListingImage {
  @Column(name = "product_image_id", nullable = false)
  private long productImageId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  protected ListingImage() {}

  public ListingImage(long productImageId, int sortOrder) {
    this.productImageId = productImageId;
    this.sortOrder = sortOrder;
    primary = sortOrder == 0;
  }

  public long getProductImageId() {
    return productImageId;
  }
}
