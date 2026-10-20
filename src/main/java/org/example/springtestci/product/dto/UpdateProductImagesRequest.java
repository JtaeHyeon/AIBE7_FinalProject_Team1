package org.example.springtestci.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 전체 이미지 순서와 대표 이미지를 한 번에 교체한다. */
public record UpdateProductImagesRequest(
    @NotEmpty @Size(max = 10) List<@NotNull @Positive Long> imageIds,
    @Positive long primaryImageId) {}
