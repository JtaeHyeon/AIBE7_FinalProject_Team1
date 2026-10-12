package org.example.springtestci.listing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;
import org.example.springtestci.listing.service.ListingDraftService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ListingDraftController {

  private final ListingDraftService service;

  public ListingDraftController(ListingDraftService service) {
    this.service = service;
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
    var response =
        saved.created()
            ? ResponseEntity.created(URI.create("/api/v1/products/" + productId + "/listing-draft"))
            : ResponseEntity.ok();
    return response.cacheControl(CacheControl.noStore()).body(ApiResponse.success(saved.draft()));
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
