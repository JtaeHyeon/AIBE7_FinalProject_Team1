package org.example.springtestci.product.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.product.dto.ProductDetailResponse;
import org.example.springtestci.product.dto.ProductSummaryResponse;
import org.example.springtestci.product.dto.RegisterProductRequest;
import org.example.springtestci.product.dto.RegisterProductResponse;
import org.example.springtestci.product.dto.UpdateProductRequest;
import org.example.springtestci.product.entity.ProductCategory;
import org.example.springtestci.product.entity.StorageType;
import org.example.springtestci.product.service.ProductDetails;
import org.example.springtestci.product.service.ProductManagementService;
import org.example.springtestci.product.service.ProductRegistrationService;
import org.example.springtestci.product.service.ProductSummary;
import org.example.springtestci.product.service.RegisterProductCommand;
import org.example.springtestci.product.service.RegisteredProduct;
import org.example.springtestci.product.service.UpdateProductCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

  @Mock private ProductRegistrationService productRegistrationService;
  @Mock private ProductManagementService productManagementService;

  @Test
  void registersProductForJwtSubjectWithoutReturningSerialNumber() {
    ProductController controller =
        new ProductController(
            productRegistrationService, productManagementService, new CurrentUserIdResolver());
    when(productRegistrationService.register(any()))
        .thenReturn(
            new RegisteredProduct(
                10L, ProductCategory.LAPTOP, "업무용 노트북", "SAMSUNG", "NT960X", null, null));

    ApiResponse<RegisterProductResponse> response = controller.register(jwt("42"), request());

    ArgumentCaptor<RegisterProductCommand> commandCaptor =
        ArgumentCaptor.forClass(RegisterProductCommand.class);
    verify(productRegistrationService).register(commandCaptor.capture());
    assertEquals(42L, commandCaptor.getValue().ownerId());
    assertEquals(10L, response.data().id());
    assertEquals("SAMSUNG", response.data().manufacturer());
  }

  @Test
  void findsOnlyProductsForJwtSubject() {
    ProductController controller = controller();
    when(productManagementService.findMine(42L))
        .thenReturn(
            List.of(
                new ProductSummary(
                    10L,
                    ProductCategory.LAPTOP,
                    "업무용 노트북",
                    "SAMSUNG",
                    "NT960X",
                    "1234",
                    LocalDate.of(2026, 1, 10))));

    ApiResponse<List<ProductSummaryResponse>> response = controller.findMine(jwt("42"));

    verify(productManagementService).findMine(42L);
    assertEquals(1, response.data().size());
    assertEquals("****1234", response.data().get(0).serialNumberMasked());
  }

  @Test
  void updatesOnlyEditableFieldsForJwtSubject() {
    ProductController controller = controller();
    when(productManagementService.update(any())).thenReturn(details("NT960X-PRO"));

    ApiResponse<ProductDetailResponse> response =
        controller.update(jwt("42"), 10L, updateRequest());

    ArgumentCaptor<UpdateProductCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateProductCommand.class);
    verify(productManagementService).update(commandCaptor.capture());
    assertEquals(42L, commandCaptor.getValue().ownerId());
    assertEquals(10L, commandCaptor.getValue().productId());
    assertEquals("NT960X-PRO", response.data().modelName());
    assertEquals("SAMSUNG", response.data().manufacturer());
  }

  private ProductController controller() {
    return new ProductController(
        productRegistrationService, productManagementService, new CurrentUserIdResolver());
  }

  private RegisterProductRequest request() {
    return new RegisterProductRequest(
        "업무용 노트북",
        "Samsung",
        "NT960X",
        "AB1234",
        null,
        null,
        new RegisterProductRequest.LaptopSpecification(
            "Intel Core Ultra 7",
            32,
            1024,
            StorageType.SSD,
            "Intel Arc",
            "16인치 2880x1800",
            (short) 92,
            "Windows 11"));
  }

  private UpdateProductRequest updateRequest() {
    return new UpdateProductRequest(
        "NT960X-PRO",
        LocalDate.of(2026, 2, 1),
        new UpdateProductRequest.LaptopSpecification(
            "Intel Core Ultra 9",
            64,
            2048,
            StorageType.SSD,
            "NVIDIA RTX",
            "16인치 2880x1800",
            (short) 95,
            "Windows 11 Pro"));
  }

  private ProductDetails details(String modelName) {
    return new ProductDetails(
        10L,
        ProductCategory.LAPTOP,
        "업무용 노트북",
        "SAMSUNG",
        modelName,
        "1234",
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2027, 1, 10),
        "Intel Core Ultra 9",
        64,
        2048,
        StorageType.SSD,
        "NVIDIA RTX",
        "16인치 2880x1800",
        (short) 95,
        "Windows 11 Pro");
  }

  private Jwt jwt(String subject) {
    return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).build();
  }
}
