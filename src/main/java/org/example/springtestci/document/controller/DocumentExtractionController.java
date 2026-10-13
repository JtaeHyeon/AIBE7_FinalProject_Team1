package org.example.springtestci.document.controller;

import jakarta.validation.Valid;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.document.dto.ExtractionCreateRequest;
import org.example.springtestci.document.dto.ExtractionResponse;
import org.example.springtestci.document.service.DocumentExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/extractions")
public class DocumentExtractionController {

  private final DocumentExtractionService extractionService;

  public DocumentExtractionController(DocumentExtractionService extractionService) {
    this.extractionService = extractionService;
  }

  /** 외부 AI 분석을 요청한다. 접수만 하고 202로 응답하며, 결과는 조회 API로 확인한다. */
  @PostMapping
  ResponseEntity<ApiResponse<ExtractionResponse>> request(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long documentId,
      @Valid @RequestBody ExtractionCreateRequest request) {
    var extraction = extractionService.request(documentId, userId(jwt), request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(ExtractionResponse.from(extraction)));
  }

  @GetMapping("/latest")
  ApiResponse<ExtractionResponse> latest(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId) {
    return ApiResponse.success(
        ExtractionResponse.from(extractionService.latest(documentId, userId(jwt))));
  }

  @GetMapping("/{extractionId}")
  ApiResponse<ExtractionResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long documentId,
      @PathVariable Long extractionId) {
    return ApiResponse.success(
        ExtractionResponse.from(extractionService.get(documentId, extractionId, userId(jwt))));
  }

  /** JWT subject를 사용자 ID로 사용한다. 인증 도메인이 확정되면 공통 컴포넌트로 옮긴다. */
  private Long userId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException exception) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
  }
}
