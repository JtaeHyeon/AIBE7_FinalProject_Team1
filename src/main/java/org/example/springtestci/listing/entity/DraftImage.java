package org.example.springtestci.listing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 초안의 선택 관계만 관리하며 제품 화면의 대표 사진과 순서를 변경하지 않는다. */
@Embeddable
public class DraftImage {

  @Column(name = "product_image_id", nullable = false)
  private long productImageId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  protected DraftImage() {}

  public DraftImage(long productImageId, int sortOrder) {
    this.productImageId = productImageId;
    this.sortOrder = sortOrder;
    this.primary = sortOrder == 0;
  }

  public long getProductImageId() {
    return productImageId;
  }
}
