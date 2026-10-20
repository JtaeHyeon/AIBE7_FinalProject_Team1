package org.example.springtestci.product.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.product.dto.CreateProductRequest;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.dto.UpdateProductImagesRequest;
import org.example.springtestci.product.dto.UpdateProductRequest;
import org.example.springtestci.product.service.ProductService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ProductResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(productService.create(userId(jwt), request)));
  }

  @GetMapping
  public ApiResponse<List<ProductResponse>> listMine(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(productService.listMine(userId(jwt)));
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId) {
    return ApiResponse.success(productService.get(userId(jwt), productId));
  }

  @PatchMapping("/{productId}")
  public ApiResponse<ProductResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @Valid @RequestBody UpdateProductRequest request) {
    return ApiResponse.success(productService.update(userId(jwt), productId, request));
  }

  @PostMapping(path = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ProductResponse>> addImage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @RequestPart("file") MultipartFile file) {
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException exception) {
      throw new CommonException(ErrorCode.INVALID_PRODUCT_IMAGE);
    }
    ProductResponse product =
        productService.addImage(
            userId(jwt), productId, file.getOriginalFilename(), file.getContentType(), content);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(product));
  }

  @PutMapping("/{productId}/images")
  public ApiResponse<ProductResponse> updateImages(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @Valid @RequestBody UpdateProductImagesRequest request) {
    return ApiResponse.success(productService.updateImages(userId(jwt), productId, request));
  }

  @DeleteMapping("/{productId}/images/{imageId}")
  public ApiResponse<ProductResponse> deleteImage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @PathVariable @Positive long imageId) {
    return ApiResponse.success(productService.deleteImage(userId(jwt), productId, imageId));
  }

  @GetMapping("/{productId}/images/{imageId}")
  public ResponseEntity<Resource> image(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @PathVariable @Positive long imageId) {
    var image = productService.image(userId(jwt), productId, imageId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.resource());
  }

  private long userId(Jwt jwt) {
    try {
      return Long.parseLong(jwt.getSubject());
    } catch (NumberFormatException exception) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
  }
}
