package org.example.springtestci.listing.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.example.springtestci.product.dto.ProductCategory;

/** 공개 목록의 조건. 제공한 조건은 모두 AND로 조합하고, 사양이 없는 제품은 사양 조건에 맞지 않는다. */
public record ListingSearchRequest(
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size,
    @Size(max = 100) String manufacturer,
    @Pattern(regexp = ProductCategory.PATTERN) String category,
    @Size(max = 100) String keyword,
    @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal minPrice,
    @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal maxPrice,
    @Size(max = 100) String cpu,
    @Size(max = 100) String gpu,
    @Positive Integer minRamGb,
    @Positive Integer minStorageGb,
    @Pattern(regexp = "SSD|HDD|MIXED") String storageType,
    @Size(max = 50) String os,
    @Min(1) @Max(5) Integer minConditionGrade,
    Sort sort) {

  public ListingSearchRequest {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
    sort = sort == null ? Sort.LATEST : sort;
    manufacturer = clean(manufacturer);
    category = clean(category);
    keyword = clean(keyword);
    cpu = clean(cpu);
    gpu = clean(gpu);
    storageType = clean(storageType);
    os = clean(os);
  }

  @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
  public boolean isPriceRangeValid() {
    return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public enum Sort {
    LATEST,
    PRICE_ASC,
    PRICE_DESC
  }
}
