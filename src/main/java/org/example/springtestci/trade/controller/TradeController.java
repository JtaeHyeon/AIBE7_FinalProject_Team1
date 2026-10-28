package org.example.springtestci.trade.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.ChangeReviewPageResponse;
import org.example.springtestci.trade.dto.ChangeReviewRequest;
import org.example.springtestci.trade.dto.ChangeReviewResponse;
import org.example.springtestci.trade.dto.TradeCancellationRequest;
import org.example.springtestci.trade.dto.TradeCancellationResponse;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.TradeCancellationService;
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

/**
 * P3 예약 취소와 예약 중 변경 확인 API 진입점이다.
 *
 * <p>외부 변경 사본 생성 API는 제공하지 않는다. 변경 사본은 정식 history/product 연동이 내부 서비스 계약으로 공급해야 한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/trades")
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class TradeController {

  private final TradeCancellationService cancellationService;
  private final ReservationChangeService changeService;
  private final CurrentUserIdResolver currentUserIdResolver;

  public TradeController(
      TradeCancellationService cancellationService,
      ReservationChangeService changeService,
      CurrentUserIdResolver currentUserIdResolver) {
    this.cancellationService = cancellationService;
    this.changeService = changeService;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  /** 구매 요청 ID와 enum 사유를 받아 원래 판매자 또는 선택 구매자의 RESERVED 거래 취소를 위임한다. */
  @PostMapping("/{tradeId}/cancel")
  public ApiResponse<TradeCancellationResponse> cancel(
      @PathVariable @Positive long tradeId,
      @Valid @RequestBody TradeCancellationRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        cancellationService.cancel(
            tradeId,
            currentUserIdResolver.resolveAuthentication(authentication),
            request.reason()));
  }

  /** 거래 당사자에게 사건 ID 오름차순 변경 사본, 결정과 미확인 건수를 페이지로 반환한다. */
  @GetMapping("/{tradeId}/change-reviews")
  public ApiResponse<ChangeReviewPageResponse> findChangeReviews(
      @PathVariable @Positive long tradeId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication authentication) {
    return ApiResponse.success(
        changeService.findAll(
            tradeId, currentUserIdResolver.resolveAuthentication(authentication), page, size));
  }

  /** 선택 구매자의 KEEP 또는 사유가 포함된 CANCEL 결정을 해당 사건에 기록한다. */
  @PostMapping("/{tradeId}/change-reviews/{eventId}")
  public ApiResponse<ChangeReviewResponse> reviewChange(
      @PathVariable @Positive long tradeId,
      @PathVariable @Positive long eventId,
      @Valid @RequestBody ChangeReviewRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        changeService.review(
            tradeId,
            eventId,
            currentUserIdResolver.resolveAuthentication(authentication),
            request.decision(),
            request.reason()));
  }
}
