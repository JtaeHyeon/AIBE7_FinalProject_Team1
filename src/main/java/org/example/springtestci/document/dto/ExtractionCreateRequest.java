package org.example.springtestci.document.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.springtestci.document.entity.DocumentType;

/** 외부 AI 전송에 대한 명시적 동의. 동의하지 않으면 추출을 요청할 수 없다. */
public record ExtractionCreateRequest(
    @AssertTrue(message = "외부 AI 전송에 동의해야 문서를 분석할 수 있습니다.") boolean consented,
    @NotBlank @Size(max = 30) String consentNoticeVersion,
    @NotNull DocumentType documentType) {}
