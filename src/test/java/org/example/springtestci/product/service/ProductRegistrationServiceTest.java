package org.example.springtestci.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProductRegistrationServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private LaptopDetailRepository laptopDetailRepository;

  private ProductRegistrationService service;

  @BeforeEach
  void setUp() {
    SerialNumberProtector serialNumberProtector =
        new SerialNumberProtector(
            new SerialNumberProperties("test-serial-fingerprint-secret-at-least-32-bytes"));
    service =
        new ProductRegistrationService(
            productRepository, laptopDetailRepository, serialNumberProtector);
  }

  @Test
  void registersLaptopWithAuthenticatedUserAsOwner() {
    when(productRepository.existsBySerialNumberFingerprint(any())).thenReturn(false);
    when(productRepository.saveAndFlush(any(Product.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RegisteredProduct registered = service.register(command(" ab-12 34 "));

    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    ArgumentCaptor<LaptopDetail> detailCaptor = ArgumentCaptor.forClass(LaptopDetail.class);
    verify(productRepository).saveAndFlush(productCaptor.capture());
    verify(laptopDetailRepository).save(detailCaptor.capture());

    Product product = productCaptor.getValue();
    assertEquals(42L, product.getOwnerId());
    assertEquals("SAMSUNG", product.getManufacturer());
    assertEquals("1234", product.getSerialLast4());
    assertEquals(product, detailCaptor.getValue().getProduct());
    assertEquals(32, detailCaptor.getValue().getRamGb());
    assertEquals("SAMSUNG", registered.manufacturer());
  }

  @Test
  void rejectsDuplicateManufacturerAndSerialNumber() {
    when(productRepository.existsBySerialNumberFingerprint(any())).thenReturn(true);

    CommonException exception =
        assertThrows(CommonException.class, () -> service.register(command("AB1234")));

    assertEquals(ErrorCode.PRODUCT_ALREADY_REGISTERED, exception.errorCode());
    verify(productRepository, never()).saveAndFlush(any());
    verify(laptopDetailRepository, never()).save(any());
  }

  @Test
  void convertsConcurrentUniqueConstraintViolationToDuplicateError() {
    when(productRepository.existsBySerialNumberFingerprint(any())).thenReturn(false);
    when(productRepository.saveAndFlush(any(Product.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate fingerprint"));

    CommonException exception =
        assertThrows(CommonException.class, () -> service.register(command("AB1234")));

    assertEquals(ErrorCode.PRODUCT_ALREADY_REGISTERED, exception.errorCode());
    verify(laptopDetailRepository, never()).save(any());
  }

  private RegisterProductCommand command(String serialNumber) {
    return new RegisterProductCommand(
        42L,
        "업무용 노트북",
        " Samsung ",
        "NT960X",
        serialNumber,
        LocalDate.of(2026, 1, 10),
        LocalDate.of(2027, 1, 10),
        "Intel Core Ultra 7",
        32,
        1024,
        StorageType.SSD,
        "Intel Arc",
        "16인치 2880x1800",
        (short) 92,
        "Windows 11");
  }
}
