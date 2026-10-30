package org.example.springtestci.product.dto;

/** 제품 분류 코드. DB의 ck_products_category 제약과 같은 값을 유지한다. */
public final class ProductCategory {
  public static final String DEFAULT = "LAPTOP";
  public static final String PATTERN =
      "LAPTOP|MOBILE|DISPLAY|LARGE_APPLIANCE|CLEANING_AIR|CLIMATE|KITCHEN|AUDIO_GAME";

  private ProductCategory() {}
}
