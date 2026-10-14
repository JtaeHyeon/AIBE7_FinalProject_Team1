package org.example.springtestci.product.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.product.dto.ProductDetailResponse;
import org.example.springtestci.product.dto.ProductSummaryResponse;
import org.example.springtestci.product.dto.RegisterProductRequest;
import org.example.springtestci.product.dto.RegisterProductResponse;
import org.example.springtestci.product.dto.UpdateProductRequest;
import org.example.springtestci.product.service.ProductManagementService;
import org.example.springtestci.product.service.ProductRegistrationService;
import org.example.springtestci.product.service.RegisteredProduct;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

  private final ProductRegistrationService productRegistrationService;
  private final ProductManagementService productManagementService;
  private final CurrentUserIdResolver currentUserIdResolver;

  public ProductController(
      ProductRegistrationService productRegistrationService,
      ProductManagementService productManagementService,
      CurrentUserIdResolver currentUserIdResolver) {
    this.productRegistrationService = productRegistrationService;
    this.productManagementService = productManagementService;
    this.currentUserIdResolver = currentUserIdResolver;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<RegisterProductResponse> register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterProductRequest request) {
    long ownerId = currentUserIdResolver.resolve(jwt);
    RegisteredProduct product = productRegistrationService.register(request.toCommand(ownerId));
    return ApiResponse.success(RegisterProductResponse.from(product));
  }

  @GetMapping
  public ApiResponse<List<ProductSummaryResponse>> findMine(@AuthenticationPrincipal Jwt jwt) {
    long ownerId = currentUserIdResolver.resolve(jwt);
    List<ProductSummaryResponse> products =
        productManagementService.findMine(ownerId).stream()
            .map(ProductSummaryResponse::from)
            .toList();
    return ApiResponse.success(products);
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductDetailResponse> findMine(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId) {
    long ownerId = currentUserIdResolver.resolve(jwt);
    return ApiResponse.success(
        ProductDetailResponse.from(productManagementService.findMine(ownerId, productId)));
  }

  @PatchMapping("/{productId}")
  public ApiResponse<ProductDetailResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @Positive long productId,
      @Valid @RequestBody UpdateProductRequest request) {
    long ownerId = currentUserIdResolver.resolve(jwt);
    return ApiResponse.success(
        ProductDetailResponse.from(
            productManagementService.update(request.toCommand(ownerId, productId))));
  }
}
