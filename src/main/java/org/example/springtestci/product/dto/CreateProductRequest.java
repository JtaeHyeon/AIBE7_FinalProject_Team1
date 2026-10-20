package org.example.springtestci.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateProductRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 100) String manufacturer,
    @NotBlank @Size(max = 100) String modelName,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    @NotBlank @Size(min = 4, max = 64) String serialNumber,
    @Valid Specifications specifications) {

  public record Specifications(
      @Size(max = 100) String cpu,
      @Size(max = 100) String gpu,
      @Positive Integer ramGb,
      @Positive Integer storageGb,
      @Pattern(regexp = "SSD|HDD|MIXED", message = "SSD, HDD, MIXED 중 하나여야 합니다.")
          String storageType,
      @Size(max = 100) String display,
      @Min(0) @Max(100) Integer batteryHealthPercent,
      @Size(max = 50) String os) {}

  /** 일련번호는 로그에 남지 않도록 toString에서 가린다. */
  @Override
  public String toString() {
    return "CreateProductRequest[title=" + title + ", serialNumber=<redacted>]";
  }
}
