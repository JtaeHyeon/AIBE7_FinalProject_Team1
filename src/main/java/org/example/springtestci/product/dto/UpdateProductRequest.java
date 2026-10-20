package org.example.springtestci.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PROD-08에서 허용한 제품명, 최초 구매일, 보증 만료일과 변경 사유만 수정한다. */
public record UpdateProductRequest(
    @NotBlank @Size(max = 200) String title,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    @NotBlank @Size(max = 500) String reason) {}
