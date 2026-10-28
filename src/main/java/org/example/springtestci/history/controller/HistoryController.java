package org.example.springtestci.history.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.history.dto.CorrectHistoryEventRequest;
import org.example.springtestci.history.dto.CreateHistoryEventRequest;
import org.example.springtestci.history.dto.HistoryEventResponse;
import org.example.springtestci.history.dto.VoidHistoryEventRequest;
import org.example.springtestci.history.service.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/products/{productId}/events")
public class HistoryController {

  private final HistoryService historyService;
  private final CurrentUserIdResolver currentUserIdResolver;

  public HistoryController(
      HistoryService historyService, CurrentUserIdResolver currentUserIdResolver) {
    this.historyService = historyService;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<HistoryEventResponse>> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @Valid @RequestBody CreateHistoryEventRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                historyService.create(currentUserIdResolver.resolve(jwt), productId, request)));
  }

  @GetMapping
  public ApiResponse<List<HistoryEventResponse>> timeline(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId) {
    return ApiResponse.success(
        historyService.timeline(currentUserIdResolver.resolve(jwt), productId));
  }

  @PatchMapping("/{eventId}")
  public ApiResponse<HistoryEventResponse> correct(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @PathVariable @Positive long eventId,
      @Valid @RequestBody CorrectHistoryEventRequest request) {
    return ApiResponse.success(
        historyService.correct(currentUserIdResolver.resolve(jwt), productId, eventId, request));
  }

  @PostMapping("/{eventId}/void")
  public ApiResponse<HistoryEventResponse> voidEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @PathVariable @Positive long eventId,
      @Valid @RequestBody VoidHistoryEventRequest request) {
    return ApiResponse.success(
        historyService.voidEvent(currentUserIdResolver.resolve(jwt), productId, eventId, request));
  }
}
