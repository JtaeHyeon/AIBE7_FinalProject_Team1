package org.example.springtestci.trade.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.TradeCompletionContextResponse;
import org.example.springtestci.trade.dto.TradeCompletionRequest;
import org.example.springtestci.trade.dto.TradeCompletionResponse;
import org.example.springtestci.trade.service.TradeCompletionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P4 완료 context와 양측 완료 확인을 P3 Controller 의존성과 분리해 제공한다. */
@Validated
@RestController
@RequestMapping("/api/v1/trades")
@ConditionalOnProperty(prefix = "app.features.trade-p4", name = "enabled", havingValue = "true")
public class TradeCompletionController {

  private final TradeCompletionService completionService;
  private final CurrentUserIdResolver currentUserIdResolver;

  public TradeCompletionController(
      TradeCompletionService completionService, CurrentUserIdResolver currentUserIdResolver) {
    this.completionService = completionService;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  /** 원래 당사자에게 최신 버전과 공개 제품·이력, 양측 확인 상태를 반환한다. */
  @GetMapping("/{tradeId}/completion-context")
  public ApiResponse<TradeCompletionContextResponse> getContext(
      @PathVariable @Positive long tradeId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication authentication) {
    return ApiResponse.success(
        completionService.getContext(
            tradeId, currentUserIdResolver.resolveAuthentication(authentication), page, size));
  }

  /** 인증 사용자의 확인만 저장하고 양측 조건 충족 시 같은 요청에서 최종 완료한다. */
  @PostMapping("/{tradeId}/complete")
  public ApiResponse<TradeCompletionResponse> complete(
      @PathVariable @Positive long tradeId,
      @Valid @RequestBody TradeCompletionRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        completionService.complete(
            tradeId,
            currentUserIdResolver.resolveAuthentication(authentication),
            request.confirmedFactsVersion()));
  }
}
