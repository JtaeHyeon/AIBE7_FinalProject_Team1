package org.example.springtestci.history.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.history.dto.ChangeVisibilityRequest;
import org.example.springtestci.history.dto.CorrectEventRequest;
import org.example.springtestci.history.dto.CreateEventRequest;
import org.example.springtestci.history.dto.ProductEventResponse;
import org.example.springtestci.history.dto.VoidEventRequest;
import org.example.springtestci.history.service.ProductHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ProductHistoryController {

  private final ProductHistoryService service;
  private final CurrentUserIdResolver currentUserIdResolver;

  public ProductHistoryController(
      ProductHistoryService service, CurrentUserIdResolver currentUserIdResolver) {
    this.service = service;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  @PostMapping("/products/{productId}/events")
  public ResponseEntity<ApiResponse<ProductEventResponse>> createEvent(
      Authentication authentication,
      @PathVariable("productId") Long productId,
      @Valid @RequestBody CreateEventRequest request) {
    long actorId = currentUserIdResolver.resolveAuthentication(authentication);
    // request의 productId와 path의 productId 동기화
    CreateEventRequest effectiveRequest =
        new CreateEventRequest(
            productId,
            request.eventType(),
            request.occurredOn(),
            request.title(),
            request.content(),
            request.serviceProvider(),
            request.replacedPart(),
            request.visibility());
    ProductEventResponse response = service.createEvent(actorId, effectiveRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/products/{productId}/events")
  public ResponseEntity<ApiResponse<List<ProductEventResponse>>> getEvents(
      Authentication authentication, @PathVariable("productId") Long productId) {
    Long actorId = null;
    if (authentication != null && authentication.isAuthenticated()) {
      try {
        actorId = currentUserIdResolver.resolveAuthentication(authentication);
      } catch (Exception ignored) {
      }
    }
    List<ProductEventResponse> response = service.getEvents(actorId, productId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PutMapping("/events/{eventId}/correct")
  public ResponseEntity<ApiResponse<ProductEventResponse>> correctEvent(
      Authentication authentication,
      @PathVariable("eventId") Long eventId,
      @Valid @RequestBody CorrectEventRequest request) {
    long actorId = currentUserIdResolver.resolveAuthentication(authentication);
    ProductEventResponse response = service.correctEvent(actorId, eventId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PutMapping("/events/{eventId}/visibility")
  public ResponseEntity<ApiResponse<ProductEventResponse>> changeVisibility(
      Authentication authentication,
      @PathVariable("eventId") Long eventId,
      @Valid @RequestBody ChangeVisibilityRequest request) {
    long actorId = currentUserIdResolver.resolveAuthentication(authentication);
    ProductEventResponse response = service.changeVisibility(actorId, eventId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/events/{eventId}/void")
  public ResponseEntity<ApiResponse<ProductEventResponse>> voidEvent(
      Authentication authentication,
      @PathVariable("eventId") Long eventId,
      @Valid @RequestBody VoidEventRequest request) {
    long actorId = currentUserIdResolver.resolveAuthentication(authentication);
    ProductEventResponse response = service.voidEvent(actorId, eventId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
