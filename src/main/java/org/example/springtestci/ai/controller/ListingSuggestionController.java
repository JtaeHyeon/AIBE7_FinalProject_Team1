package org.example.springtestci.ai.controller;

import jakarta.validation.constraints.Positive;
import org.example.springtestci.ai.dto.ListingSuggestionResponse;
import org.example.springtestci.ai.service.ListingSuggestionService;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 작성 중인 판매글의 제목·본문을 제안한다. 생성 결과의 적용과 임시저장은 별도 단계다. */
@Validated
@RestController
@RequestMapping("/api/v1/products/{productId}/listing-draft")
public class ListingSuggestionController {
  private final ListingSuggestionService service;

  public ListingSuggestionController(ListingSuggestionService service) {
    this.service = service;
  }

  /** 현재 소유자에게만 제목·본문 제안을 제공한다. 사용자 프롬프트·이력 선택·다른 사용자 번호는 받지 않는다. */
  @PostMapping("/ai-suggestion")
  public ResponseEntity<ApiResponse<ListingSuggestionResponse>> generate(
      @PathVariable @Positive long productId, Authentication authentication) {
    long userId;
    try {
      userId = Long.parseLong(authentication.getName());
    } catch (RuntimeException failure) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
    if (userId <= 0) throw new CommonException(ErrorCode.UNAUTHORIZED);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(service.generate(productId, userId)));
  }
}
