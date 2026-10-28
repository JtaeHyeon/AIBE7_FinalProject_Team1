package org.example.springtestci.listing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.IOException;
import java.net.URI;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.listing.dto.CancelListingRequest;
import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.listing.dto.ListingManagementResponse;
import org.example.springtestci.listing.dto.ListingPageResponse;
import org.example.springtestci.listing.dto.ListingSearchRequest;
import org.example.springtestci.listing.dto.ListingStateResponse;
import org.example.springtestci.listing.dto.PublishListingRequest;
import org.example.springtestci.listing.dto.PublishedListingResponse;
import org.example.springtestci.listing.dto.UpdateListingRequest;
import org.example.springtestci.listing.service.ListingBrowseService;
import org.example.springtestci.listing.service.ListingDetailService;
import org.example.springtestci.listing.service.ListingManagementService;
import org.example.springtestci.listing.service.ListingPublishService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 게시된 판매글의 목록·상세·관리 요청을 받고, 실제 처리는 각 서비스에 맡긴다. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class ListingController {
  private final ListingPublishService publisher;
  private final ListingDetailService details;
  private final ListingBrowseService browse;
  private final ListingManagementService management;

  public ListingController(
      ListingPublishService publisher,
      ListingDetailService details,
      ListingBrowseService browse,
      ListingManagementService management) {
    this.publisher = publisher;
    this.details = details;
    this.browse = browse;
    this.management = management;
  }

  /** 비회원도 볼 수 있는 판매 중 목록이다. 검색·필터·정렬 조건은 서비스로 전달한다. */
  @GetMapping("/listings")
  public ResponseEntity<ApiResponse<ListingPageResponse>> list(
      @Valid @ModelAttribute ListingSearchRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(browse.list(request)));
  }

  /** 목록 카드에 필요한 대표 사진 한 장만 제공한다. 상세 사진 조회와는 공개 범위가 다르다. */
  @GetMapping("/listings/{listingId}/thumbnail")
  public ResponseEntity<Resource> thumbnail(@PathVariable @Positive long listingId)
      throws IOException {
    var image = browse.thumbnail(listingId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.resource());
  }

  /** 저장된 초안을 게시한다. 첫 게시면 201, 같은 게시 요청의 재확인이면 200을 반환한다. */
  @PostMapping("/products/{productId}/listings")
  public ResponseEntity<ApiResponse<PublishedListingResponse>> publish(
      @PathVariable @Positive long productId,
      Authentication authentication,
      @Valid @RequestBody PublishListingRequest request) {
    var result = publisher.publish(productId, userId(authentication), request);
    return ResponseEntity.status(result.created() ? 201 : 200)
        .location(URI.create("/api/v1/listings/" + result.response().listingId()))
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(result.response()));
  }

  /** 로그인 회원에게 판매 조건·선택 사진·공개 이력·AI 요약 상태를 묶어 반환한다. */
  @GetMapping("/listings/{listingId}")
  public ResponseEntity<ApiResponse<ListingDetailResponse>> detail(
      @PathVariable @Positive long listingId,
      Authentication authentication,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(details.get(listingId, userId(authentication), page, size)));
  }

  /** 판매자 전용 수정 폼을 채우는 API다. 조회용 상세 응답과 구분한다. */
  @GetMapping("/listings/{listingId}/management")
  public ResponseEntity<ApiResponse<ListingManagementResponse>> management(
      @PathVariable @Positive long listingId, Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(management.get(listingId, userId(authentication))));
  }

  /** 판매 중인 글의 제목·가격·설명·사진 선택을 수정한다. 제품 원본 사양과 이력은 수정하지 않는다. */
  @PutMapping("/listings/{listingId}")
  public ResponseEntity<ApiResponse<ListingManagementResponse>> update(
      @PathVariable @Positive long listingId,
      Authentication authentication,
      @Valid @RequestBody UpdateListingRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(management.update(listingId, userId(authentication), request)));
  }

  /** 게시글을 물리적으로 삭제하지 않고 판매 중지 상태로 바꾸고 관련 취소 처리를 요청한다. */
  @PostMapping("/listings/{listingId}/cancel")
  public ResponseEntity<ApiResponse<ListingStateResponse>> cancel(
      @PathVariable @Positive long listingId,
      Authentication authentication,
      @Valid @RequestBody CancelListingRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(management.cancel(listingId, userId(authentication), request)));
  }

  @GetMapping("/listings/{listingId}/images/{imageId}")
  public ResponseEntity<Resource> image(
      @PathVariable @Positive long listingId,
      @PathVariable @Positive long imageId,
      Authentication authentication)
      throws IOException {
    userId(authentication);
    var image = details.image(listingId, imageId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.resource());
  }

  private long userId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated())
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    try {
      long id = Long.parseLong(authentication.getName());
      if (id > 0) return id;
    } catch (NumberFormatException ignored) {
    }
    throw new CommonException(ErrorCode.UNAUTHORIZED);
  }
}
