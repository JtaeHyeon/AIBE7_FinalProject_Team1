package org.example.springtestci.admin.controller;

import org.example.springtestci.admin.dto.AdminDtos.*;
import org.example.springtestci.admin.service.AdminReadService;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** ADM-01~04 조회 전용 API. 관리자 권한은 SecurityConfig와 서비스 양쪽에서 검사한다. */
@RestController
public class AdminController {
  private final AdminReadService service;
  private final CurrentUserIdResolver userIds;

  public AdminController(AdminReadService service, CurrentUserIdResolver userIds) {
    this.service = service;
    this.userIds = userIds;
  }

  @GetMapping(
      value = {"/admin", "/admin/"},
      produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> page() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new ClassPathResource("static/admin.html"));
  }

  @GetMapping("/api/v1/admin/trades")
  public ApiResponse<Page<Trade>> trades(
      @AuthenticationPrincipal Jwt jwt, @ModelAttribute Filter filter) {
    return ApiResponse.success(service.trades(userIds.resolve(jwt), filter));
  }

  @GetMapping("/api/v1/admin/trades/{id}")
  public ApiResponse<Trade> trade(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return ApiResponse.success(service.trade(userIds.resolve(jwt), id));
  }

  @GetMapping("/api/v1/admin/audit-logs")
  public ApiResponse<Page<Audit>> audits(
      @AuthenticationPrincipal Jwt jwt, @ModelAttribute Filter filter) {
    return ApiResponse.success(service.audits(userIds.resolve(jwt), filter));
  }

  @GetMapping("/api/v1/admin/audit-logs/{id}")
  public ApiResponse<AuditDetail> audit(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return ApiResponse.success(service.audit(userIds.resolve(jwt), id));
  }
}
