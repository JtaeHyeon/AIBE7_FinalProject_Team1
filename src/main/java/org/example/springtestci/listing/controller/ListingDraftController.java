package org.example.springtestci.listing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.example.springtestci.listing.dto.ListingDraftPageResponse;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;
import org.example.springtestci.listing.service.ListingDraftService;
import org.example.springtestci.listing.service.ListingPreviewService;
import org.example.springtestci.listing.service.ListingPublicationService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ListingDraftController {

  private final ListingDraftService service;
  private final ListingPublicationService publications;
  private final ListingPreviewService previews;

  public ListingDraftController(
      ListingDraftService service,
      ListingPublicationService publications,
      ListingPreviewService previews) {
    this.service = service;
    this.publications = publications;
    this.previews = previews;
  }

  @GetMapping("/products/{productId}/listing-draft/preview")
  public ResponseEntity<ApiResponse<ListingPreviewResponse>> preview(
      @PathVariable @Positive long productId,
      @RequestParam @Positive long draftId,
      @RequestParam @PositiveOrZero long version,
      Authentication authentication,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                previews.get(productId, draftId, version, userId(authentication), page, size)));
  }

  @GetMapping("/products/{productId}/listing-draft/preview/images/{imageId}")
  public ResponseEntity<Resource> previewImage(
      @PathVariable @Positive long productId,
      @PathVariable @Positive long imageId,
      @RequestParam @Positive long draftId,
      @RequestParam @PositiveOrZero long version,
      Authentication authentication)
      throws IOException {
    var image = previews.image(productId, draftId, version, imageId, userId(authentication));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.resource());
  }

  /** 현재 소유자에게 공개 예정 제품 정보와 전체 누적 이력의 한 페이지를 제공한다. */
  @GetMapping("/products/{productId}/listing-draft/publication")
  public ResponseEntity<ApiResponse<ListingPublicationResponse>> publication(
      @PathVariable @Positive long productId,
      Authentication authentication,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(publications.get(productId, userId(authentication), page, size)));
  }

  @GetMapping("/listing-drafts")
  public ResponseEntity<ApiResponse<ListingDraftPageResponse>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(service.list(userId(authentication), page, size)));
  }

  @GetMapping("/products/{productId}/listing-draft")
  public ResponseEntity<ApiResponse<ListingDraftResponse>> get(
      @PathVariable @Positive long productId, Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(service.get(productId, userId(authentication))));
  }

  @PutMapping("/products/{productId}/listing-draft")
  public ResponseEntity<ApiResponse<ListingDraftResponse>> update(
      @PathVariable @Positive long productId,
      @RequestParam @Positive long draftId,
      Authentication authentication,
      @Valid @RequestBody SaveListingDraftRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                service.update(productId, draftId, userId(authentication), request)));
  }

  @DeleteMapping("/products/{productId}/listing-draft")
  public ResponseEntity<Void> delete(
      @PathVariable @Positive long productId,
      @RequestParam @Positive long draftId,
      @RequestParam @PositiveOrZero long version,
      Authentication authentication) {
    service.delete(productId, draftId, userId(authentication), version);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @GetMapping("/listing-draft-products")
  public ResponseEntity<ApiResponse<List<DraftProductResponse>>> products(
      Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(service.products(userId(authentication))));
  }

  @GetMapping("/products/{productId}/listing-draft/composition")
  public ResponseEntity<ApiResponse<DraftProductResponse>> composition(
      @PathVariable @Positive long productId, Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(service.composition(productId, userId(authentication))));
  }

  @PostMapping("/products/{productId}/listing-draft")
  public ResponseEntity<ApiResponse<ListingDraftResponse>> save(
      @PathVariable @Positive long productId,
      Authentication authentication,
      @Valid @RequestBody SaveListingDraftRequest request) {
    var saved = service.save(productId, userId(authentication), request);
    return ResponseEntity.created(URI.create("/api/v1/products/" + productId + "/listing-draft"))
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(saved));
  }

  @GetMapping("/products/{productId}/listing-draft/images/{imageId}")
  public ResponseEntity<Resource> image(
      @PathVariable @Positive long productId,
      @PathVariable @Positive long imageId,
      Authentication authentication)
      throws IOException {
    var image = service.image(productId, imageId, userId(authentication));
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
