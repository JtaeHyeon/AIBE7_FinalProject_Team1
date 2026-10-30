package org.example.springtestci.ai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.example.springtestci.ai.dto.ProductAnswerResponse;
import org.example.springtestci.ai.dto.ProductQuestionRequest;
import org.example.springtestci.ai.service.ProductQuestionService;
import org.example.springtestci.ai.service.ProductQuestionSource.Target;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 제품은 소유자 범위, 판매글은 로그인 회원의 공개 범위로 질문한다. 비회원 질문은 허용하지 않는다. */
@Validated
@RestController
public class ProductQuestionController {
  private final ProductQuestionService service;
  private final CurrentUserIdResolver users;

  public ProductQuestionController(ProductQuestionService service, CurrentUserIdResolver users) {
    this.service = service;
    this.users = users;
  }

  @PostMapping("/api/v1/products/{productId}/ai/questions")
  public ResponseEntity<ApiResponse<ProductAnswerResponse>> product(
      @PathVariable @Positive long productId,
      Authentication authentication,
      @Valid @RequestBody ProductQuestionRequest request) {
    return answer(new Target(false, productId), authentication, request);
  }

  @PostMapping("/api/v1/listings/{listingId}/ai/questions")
  public ResponseEntity<ApiResponse<ProductAnswerResponse>> listing(
      @PathVariable @Positive long listingId,
      Authentication authentication,
      @Valid @RequestBody ProductQuestionRequest request) {
    return answer(new Target(true, listingId), authentication, request);
  }

  private ResponseEntity<ApiResponse<ProductAnswerResponse>> answer(
      Target target, Authentication authentication, ProductQuestionRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Vary", "Cookie, Authorization")
        .body(
            ApiResponse.success(
                service.ask(
                    target, users.resolveAuthentication(authentication), request.question())));
  }
}
