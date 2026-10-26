package org.example.springtestci.ai.controller;

import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.example.springtestci.ai.dto.ListingSummaryResponse;
import org.example.springtestci.ai.service.ListingSummaryStore;
import org.example.springtestci.common.error.*;
import org.example.springtestci.common.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/listings/{listingId}/summary")
public class ListingSummaryController {
  private final ListingSummaryStore store;

  public ListingSummaryController(ListingSummaryStore store) {
    this.store = store;
  }

  /** 회원에게 공개 가능한 상태·요약만 조회한다. GET으로 모델을 호출하지 않는다. */
  @GetMapping
  public ResponseEntity<ApiResponse<ListingSummaryResponse>> get(
      @PathVariable @Positive long listingId, Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(store.get(listingId, userId(authentication))));
  }

  /** 현재 소유자인 원래 게시자만 최초 생성·실패 재시도를 접수한다. 결과를 기다리지 않고 202로 응답한다. */
  @PostMapping
  public ResponseEntity<ApiResponse<ListingSummaryResponse>> request(
      @PathVariable @Positive long listingId, Authentication authentication) {
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/listings/" + listingId + "/summary"))
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(store.enqueue(listingId, userId(authentication))));
  }

  private long userId(Authentication auth) {
    try {
      long id = Long.parseLong(auth.getName());
      if (id > 0) return id;
    } catch (RuntimeException ignored) {
    }
    throw new CommonException(ErrorCode.UNAUTHORIZED);
  }
}
