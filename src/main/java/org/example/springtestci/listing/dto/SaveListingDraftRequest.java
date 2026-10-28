package org.example.springtestci.listing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 작성 폼에서 받은 임시저장 데이터다. 최초 저장은 version 없이, 수정은 조회한 version과 함께 보낸다. */
public record SaveListingDraftRequest(
    @Size(max = 60) String title,
    @Size(max = 2000) String description,
    @DecimalMin("1") @Digits(integer = 12, fraction = 0) BigDecimal price,
    @Min(1) @Max(5) Integer conditionGrade,
    @Size(max = 20) List<@NotNull @Positive Long> selectedImageIds,
    @PositiveOrZero Long version) {

  public List<Long> imageIds() {
    return selectedImageIds == null ? List.of() : List.copyOf(selectedImageIds);
  }
}
