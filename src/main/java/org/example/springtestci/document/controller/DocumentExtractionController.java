package org.example.springtestci.document.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.document.dto.ExtractionConfirmRequest;
import org.example.springtestci.document.dto.ExtractionCreateRequest;
import org.example.springtestci.document.dto.ExtractionResponse;
import org.example.springtestci.document.service.DocumentExtractionReviewService;
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

/**
 * 문서의 AI 제품 정보 추출 API. 요청·조회는 {@link DocumentExtractionService}, 확정·반려는 {@link
 * DocumentExtractionReviewService}가 처리한다.
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/extractions")
@RequiredArgsConstructor
public class DocumentExtractionController {

  private final DocumentExtractionService extractionService;
  private final DocumentExtractionReviewService reviewService;

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

  /** 문서의 가장 최근 추출 기록을 조회한다. 재요청하면 새 기록이 생기므로 번호가 가장 큰 것을 준다. */
  @GetMapping("/latest")
  ApiResponse<ExtractionResponse> latest(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId) {
    return ApiResponse.success(
        ExtractionResponse.from(extractionService.latest(documentId, userId(jwt))));
  }

  /** 추출 번호로 기록 하나를 조회한다. 상태(QUEUED~EXTRACTED 등)와 추출된 후보값을 확인한다. */
  @GetMapping("/{extractionId}")
  ApiResponse<ExtractionResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long documentId,
      @PathVariable Long extractionId) {
    return ApiResponse.success(
        ExtractionResponse.from(extractionService.get(documentId, extractionId, userId(jwt))));
  }

  /** 사용자가 검토·수정한 값으로 확정한다. AI 후보값은 사용하지 않는다. */
  @PostMapping("/{extractionId}/confirm")
  ApiResponse<ExtractionResponse> confirm(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long documentId,
      @PathVariable Long extractionId,
      @Valid @RequestBody ExtractionConfirmRequest request) {
    return ApiResponse.success(
        ExtractionResponse.from(
            reviewService.confirm(documentId, extractionId, userId(jwt), request.reviewedData())));
  }

  @PostMapping("/{extractionId}/reject")
  ApiResponse<ExtractionResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long documentId,
      @PathVariable Long extractionId) {
    return ApiResponse.success(
        ExtractionResponse.from(reviewService.reject(documentId, extractionId, userId(jwt))));
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
