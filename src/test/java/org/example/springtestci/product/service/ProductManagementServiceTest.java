package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.product.entity.LaptopDetail;
import org.example.springtestci.product.entity.Product;
import org.example.springtestci.product.entity.StorageType;
import org.example.springtestci.product.repository.LaptopDetailRepository;
import org.example.springtestci.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductManagementServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private LaptopDetailRepository laptopDetailRepository;

  private ProductManagementService service;

  @BeforeEach
  void setUp() {
    service = new ProductManagementService(productRepository, laptopDetailRepository);
  }

  @Test
  void findsOnlyActiveProductsOwnedByAuthenticatedUser() {
    Product product = product();
    when(productRepository.findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(42L))
        .thenReturn(List.of(product));

    List<ProductSummary> products = service.findMine(42L);

    verify(productRepository).findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(42L);
    assertEquals(1, products.size());
    assertEquals("SAMSUNG", products.get(0).manufacturer());
    assertEquals("1234", products.get(0).serialLast4());
  }

  @Test
  void hidesOtherUsersProductAsNotFound() {
    when(laptopDetailRepository.findOwnedActiveDetail(10L, 42L)).thenReturn(Optional.empty());

    CommonException exception =
        assertThrows(CommonException.class, () -> service.findMine(42L, 10L));

    assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.errorCode());
  }

  @Test
  void updatesModelPurchaseDateAndSpecificationWithoutChangingManufacturerOrSerial() {
    Product product = product();
    LaptopDetail detail = detail(product);
    when(laptopDetailRepository.findOwnedActiveDetail(10L, 42L)).thenReturn(Optional.of(detail));

    ProductDetails updated = service.update(updateCommand());

    assertEquals("NT960X-PRO", updated.modelName());
    assertEquals(LocalDate.of(2026, 2, 1), updated.purchaseDate());
    assertEquals("새 CPU", updated.cpuSpec());
    assertEquals(32, updated.ramGb());
    assertEquals("SAMSUNG", updated.manufacturer());
    assertEquals("1234", updated.serialLast4());
  }

  private Product product() {
    return Product.laptop(
        42L,
        "업무용 노트북",
        "SAMSUNG",
        "NT960X",
        LocalDate.of(2026, 1, 10),
        LocalDate.of(2027, 1, 10),
        "fingerprint",
        "1234");
  }

  private LaptopDetail detail(Product product) {
    return new LaptopDetail(product, "기존 CPU", 16, 512, StorageType.SSD, null, null, null, null);
  }

  private UpdateProductCommand updateCommand() {
    return new UpdateProductCommand(
        42L,
        10L,
        "NT960X-PRO",
        LocalDate.of(2026, 2, 1),
        "새 CPU",
        32,
        1024,
        StorageType.SSD,
        "새 GPU",
        "새 화면",
        (short) 95,
        "새 OS");
  }
}
