package org.example.springtestci.trade.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.CreatePurchaseRequestRequest;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.dto.PurchaseRequestPageResponse;
import org.example.springtestci.trade.dto.PurchaseRequestResponse;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.service.PurchaseRequestService;
import org.example.springtestci.trade.service.PurchaseRequestService.CreateResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * P2 구매 요청·예약 API 진입점이다.
 *
 * <p>인증 사용자 식별과 입력 검증만 담당하며 구매자·판매자 권한, 상태 전이와 트랜잭션은 {@link PurchaseRequestService}에 위임한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(
    prefix = "app.features.trade-purchase-requests",
    name = "enabled",
    havingValue = "true")
public class PurchaseRequestController {

  private static final String DEFAULT_PAGE = "0";
  private static final String DEFAULT_PAGE_SIZE = "20";
  private static final int MAX_PAGE_SIZE = 100;

  private final PurchaseRequestService service;
  private final CurrentUserIdResolver currentUserIdResolver;

  public PurchaseRequestController(
      PurchaseRequestService service, CurrentUserIdResolver currentUserIdResolver) {
    this.service = service;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  /** 게시 판매글 ID와 본문의 채팅방 ID를 결합해 구매 요청을 만들고 신규 여부에 따라 201 또는 200을 반환한다. */
  @PostMapping("/market/listings/{listingId}/purchase-requests")
  public ResponseEntity<ApiResponse<PurchaseRequestResponse>> create(
      @PathVariable @Positive long listingId,
      @Valid @RequestBody CreatePurchaseRequestRequest request,
      Authentication authentication) {
    CreateResult result = service.create(listingId, request.roomId(), actorId(authentication));
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
  }

  /** 인증 사용자 관점의 송수신 방향, 선택 상태·방 필터와 페이지 조건으로 요청 목록을 조회한다. */
  @GetMapping("/purchase-requests")
  public ApiResponse<PurchaseRequestPageResponse> findAll(
      @RequestParam PurchaseRequestDirection direction,
      @RequestParam(required = false) PurchaseRequestStatus status,
      @RequestParam(required = false) @Positive Long roomId,
      @RequestParam(defaultValue = DEFAULT_PAGE) @Min(0) int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size,
      Authentication authentication) {
    return ApiResponse.success(
        service.findAll(actorId(authentication), direction, status, roomId, page, size));
  }

  /** 구매 요청 ID를 조회하며 연결된 방의 당사자에게만 결과를 반환한다. */
  @GetMapping("/purchase-requests/{requestId}")
  public ApiResponse<PurchaseRequestResponse> findById(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.findById(requestId, actorId(authentication)));
  }

  /** 선택 구매자가 수락 전 PENDING 요청을 취소한다. */
  @PostMapping("/purchase-requests/{requestId}/cancel")
  public ApiResponse<PurchaseRequestResponse> cancel(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.cancel(requestId, actorId(authentication)));
  }

  /** 판매자가 요청을 수락해 예약과 이전 준비를 생성하고 201을 반환한다. */
  @PostMapping("/purchase-requests/{requestId}/accept")
  public ResponseEntity<ApiResponse<PurchaseRequestResponse>> accept(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.accept(requestId, actorId(authentication))));
  }

  /** 판매자가 수락 전 PENDING 요청을 거절한다. */
  @PostMapping("/purchase-requests/{requestId}/reject")
  public ApiResponse<PurchaseRequestResponse> reject(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.reject(requestId, actorId(authentication)));
  }

  /** 공통 인증 Resolver에서 현재 회원 ID를 가져오며 요청 본문의 사용자 값은 사용하지 않는다. */
  private long actorId(Authentication authentication) {
    return currentUserIdResolver.resolveAuthentication(authentication);
  }
}
