package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.product.dto.CreateProductRequest;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  private static final long OWNER = 1L;
  private static final long PRODUCT = 100L;
  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

  @Mock private ProductRepository productRepository;
  @Mock private FileStorage fileStorage;
  @Mock private ObjectProvider<FileStorage> storageProvider;

  private SerialNumberProtector protector;
  private ProductService service;

  @BeforeEach
  void setUp() {
    protector = new SerialNumberProtector("unit-test-product-serial-secret-32-bytes!!");
    service =
        new ProductService(
            productRepository, protector, new ProductImageValidator(), storageProvider);
  }

  @Test
  void createStoresOnlyTheFingerprintAndCiphertextOfTheSerialNumber() {
    CreateProductRequest request = request("ab12 cd34");
    when(productRepository.existsBySerialFingerprint(anyString())).thenReturn(false);
    when(productRepository.insertProduct(eq(OWNER), eq(request), anyString(), anyString()))
        .thenReturn(PRODUCT);
    when(productRepository.findOwned(PRODUCT, OWNER)).thenReturn(Optional.of(product()));
    when(productRepository.findImageIds(PRODUCT)).thenReturn(List.of());

    ProductResponse response = service.create(OWNER, request);

    assertEquals(PRODUCT, response.productId());
    verify(productRepository)
        .insertProduct(eq(OWNER), eq(request), eq(protector.fingerprint("AB12CD34")), anyString());
    verify(productRepository).insertLaptopDetails(PRODUCT, request.specifications());
  }

  @Test
  void createRejectsADuplicateSerialNumber() {
    when(productRepository.existsBySerialFingerprint(anyString())).thenReturn(true);

    assertCode(
        ErrorCode.PRODUCT_DUPLICATE_SERIAL, () -> service.create(OWNER, request("AB12CD34")));

    verify(productRepository, never()).insertProduct(anyLong(), any(), anyString(), anyString());
  }

  @Test
  void createMapsAConcurrentUniqueViolationToADuplicateError() {
    when(productRepository.existsBySerialFingerprint(anyString())).thenReturn(false);
    when(productRepository.insertProduct(anyLong(), any(), anyString(), anyString()))
        .thenThrow(new DuplicateKeyException("uk_products_serial_number_fingerprint"));

    assertCode(
        ErrorCode.PRODUCT_DUPLICATE_SERIAL, () -> service.create(OWNER, request("AB12CD34")));

    verify(productRepository, never()).insertLaptopDetails(anyLong(), any());
  }

  @Test
  void getHidesAProductOfAnotherOwnerAsNotFound() {
    when(productRepository.findOwned(PRODUCT, 2L)).thenReturn(Optional.empty());

    assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.get(2L, PRODUCT));
  }

  @Test
  void listMineAttachesImageUrlsThatOnlyTheOwnerCanOpen() {
    when(productRepository.findAllOwned(OWNER)).thenReturn(List.of(product()));
    when(productRepository.findImageIds(PRODUCT)).thenReturn(List.of(7L));

    List<ProductResponse> products = service.listMine(OWNER);

    assertEquals(
        "/api/v1/products/100/listing-draft/images/7", products.get(0).images().get(0).url());
  }

  @Test
  void addImageForAnotherOwnersProductIsNotFoundAndStoresNothing() {
    when(productRepository.lockOwned(PRODUCT, 2L)).thenReturn(false);

    assertCode(
        ErrorCode.RESOURCE_NOT_FOUND,
        () -> service.addImage(2L, PRODUCT, "a.png", "image/png", PNG));

    verify(storageProvider, never()).getIfAvailable();
  }

  @Test
  void addImageRejectsAnInvalidFileBeforeStoringIt() {
    when(productRepository.lockOwned(PRODUCT, OWNER)).thenReturn(true);

    assertCode(
        ErrorCode.INVALID_PRODUCT_IMAGE,
        () -> service.addImage(OWNER, PRODUCT, "a.png", "image/png", "not an image".getBytes()));

    verify(storageProvider, never()).getIfAvailable();
  }

  @Test
  void addImageRejectsTheEleventhImage() {
    when(productRepository.lockOwned(PRODUCT, OWNER)).thenReturn(true);
    when(productRepository.countImages(PRODUCT)).thenReturn(ProductService.MAX_IMAGES_PER_PRODUCT);

    assertCode(
        ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED,
        () -> service.addImage(OWNER, PRODUCT, "a.png", "image/png", PNG));

    verify(storageProvider, never()).getIfAvailable();
  }

  @Test
  void addImageStoresTheFileAndRecordsItsKey() throws IOException {
    when(productRepository.lockOwned(PRODUCT, OWNER)).thenReturn(true);
    when(productRepository.countImages(PRODUCT)).thenReturn(0);
    when(storageProvider.getIfAvailable()).thenReturn(fileStorage);
    when(fileStorage.store(eq("product-images/100"), eq("png"), any(InputStream.class)))
        .thenReturn("product-images/100/key.png");
    when(productRepository.findOwned(PRODUCT, OWNER)).thenReturn(Optional.of(product()));
    when(productRepository.findImageIds(PRODUCT)).thenReturn(List.of(1L));

    ProductResponse response = service.addImage(OWNER, PRODUCT, "a.png", "image/png", PNG);

    verify(productRepository).insertImage(PRODUCT, "product-images/100/key.png", "image/png");
    assertEquals(1, response.images().size());
  }

  @Test
  void addImageDeletesTheStoredFileWhenTheDatabaseInsertFails() throws IOException {
    when(productRepository.lockOwned(PRODUCT, OWNER)).thenReturn(true);
    when(productRepository.countImages(PRODUCT)).thenReturn(0);
    when(storageProvider.getIfAvailable()).thenReturn(fileStorage);
    when(fileStorage.store(anyString(), anyString(), any(InputStream.class)))
        .thenReturn("product-images/100/key.png");
    when(productRepository.insertImage(anyLong(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("db down"));

    assertThrows(
        IllegalStateException.class,
        () -> service.addImage(OWNER, PRODUCT, "a.png", "image/png", PNG));

    verify(fileStorage).delete("product-images/100/key.png");
  }

  @Test
  void addImageReportsUnavailableWhenNoStorageIsConfigured() {
    when(productRepository.lockOwned(PRODUCT, OWNER)).thenReturn(true);
    when(productRepository.countImages(PRODUCT)).thenReturn(0);
    when(storageProvider.getIfAvailable()).thenReturn(null);

    assertCode(
        ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE,
        () -> service.addImage(OWNER, PRODUCT, "a.png", "image/png", PNG));
  }

  private static CreateProductRequest request(String serialNumber) {
    return new CreateProductRequest(
        "MacBook Pro 14",
        "Apple",
        "MacBook Pro 14",
        null,
        null,
        serialNumber,
        new CreateProductRequest.Specifications(
            "Apple M3 Pro", null, 18, 512, "SSD", "14인치", 92, "macOS"));
  }

  private static ProductResponse product() {
    return new ProductResponse(
        PRODUCT,
        "MacBook Pro 14",
        "Apple",
        "MacBook Pro 14",
        null,
        null,
        new ProductResponse.Specifications(null, null, null, null, null, null, null, null),
        List.of(),
        OffsetDateTime.parse("2026-10-15T00:00:00Z"));
  }

  private static void assertCode(ErrorCode expected, Runnable action) {
    CommonException exception = assertThrows(CommonException.class, action::run);
    assertEquals(expected, exception.errorCode());
  }
}
