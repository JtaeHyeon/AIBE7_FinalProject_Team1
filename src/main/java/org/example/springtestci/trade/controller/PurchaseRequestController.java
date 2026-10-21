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

  @PostMapping("/market/listings/{listingId}/purchase-requests")
  public ResponseEntity<ApiResponse<PurchaseRequestResponse>> create(
      @PathVariable @Positive long listingId,
      @Valid @RequestBody CreatePurchaseRequestRequest request,
      Authentication authentication) {
    CreateResult result = service.create(listingId, request.roomId(), actorId(authentication));
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
  }

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

  @GetMapping("/purchase-requests/{requestId}")
  public ApiResponse<PurchaseRequestResponse> findById(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.findById(requestId, actorId(authentication)));
  }

  @PostMapping("/purchase-requests/{requestId}/cancel")
  public ApiResponse<PurchaseRequestResponse> cancel(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.cancel(requestId, actorId(authentication)));
  }

  @PostMapping("/purchase-requests/{requestId}/accept")
  public ResponseEntity<ApiResponse<PurchaseRequestResponse>> accept(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.accept(requestId, actorId(authentication))));
  }

  @PostMapping("/purchase-requests/{requestId}/reject")
  public ApiResponse<PurchaseRequestResponse> reject(
      @PathVariable @Positive long requestId, Authentication authentication) {
    return ApiResponse.success(service.reject(requestId, actorId(authentication)));
  }

  private long actorId(Authentication authentication) {
    return currentUserIdResolver.resolveAuthentication(authentication);
  }
}
