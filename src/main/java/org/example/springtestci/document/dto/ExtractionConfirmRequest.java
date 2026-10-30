package org.example.springtestci.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 사용자가 원본과 대조해 확인·수정한 값. 이 값만 제품 정보와 이력에 반영한다. */
public record ExtractionConfirmRequest(@NotNull @Valid ExtractedFields reviewedData) {}
