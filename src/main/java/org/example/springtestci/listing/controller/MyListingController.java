package org.example.springtestci.listing.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.listing.dto.MyListingPageResponse;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.service.MyListingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 ID를 입력받지 않고 JWT 주체로 내 판매글 범위를 고정한다. 비공개 응답은 캐시하지 않는다. */
@Validated
@RestController
public class MyListingController {
  private final MyListingService service;

  public MyListingController(MyListingService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/my-listings")
  public ResponseEntity<ApiResponse<MyListingPageResponse>> list(
      Authentication authentication,
      @RequestParam(required = false) ListingStatus status,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    long sellerId;
    try {
      if (authentication == null || !authentication.isAuthenticated())
        throw new NumberFormatException();
      sellerId = Long.parseLong(authentication.getName());
    } catch (NumberFormatException ignored) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Vary", "Cookie, Authorization")
        .body(ApiResponse.success(service.list(sellerId, status, page, size)));
  }
}
