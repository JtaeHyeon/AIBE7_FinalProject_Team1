package org.example.springtestci.product.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductModelTest {

  @Test
  void createsLaptopWithCommonInformationAndLaptopSpecification() {
    LocalDate purchasedAt = LocalDate.of(2026, 1, 10);
    LocalDate warrantyExpiresAt = LocalDate.of(2027, 1, 10);
    Product product =
        Product.laptop(
            1L,
            "업무용 노트북",
            "SAMSUNG",
            "NT960X",
            purchasedAt,
            warrantyExpiresAt,
            "fingerprint",
            "1234");

    LaptopDetail detail =
        new LaptopDetail(
            product,
            "Intel Core Ultra 7",
            32,
            1024,
            StorageType.SSD,
            "Intel Arc",
            "16인치 2880x1800",
            (short) 92,
            "Windows 11");

    assertEquals(1L, product.getOwnerId());
    assertEquals(ProductCategory.LAPTOP, product.getCategory());
    assertEquals("업무용 노트북", product.getTitle());
    assertEquals("SAMSUNG", product.getManufacturer());
    assertEquals("NT960X", product.getModelName());
    assertEquals(purchasedAt, product.getPurchaseDate());
    assertEquals(warrantyExpiresAt, product.getWarrantyExpiresAt());
    assertEquals(product, detail.getProduct());
    assertEquals("16인치 2880x1800", detail.getDisplaySpec());
    assertEquals((short) 92, detail.getBatteryHealthPercent());

    product.transferOwnershipTo(2L);
    product.archive(Instant.parse("2026-10-12T00:00:00Z"));

    assertEquals(2L, product.getOwnerId());
    assertEquals(true, product.isArchived());
  }

  @Test
  void rejectsInvalidRequiredValuesAndSpecificationRanges() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Product.laptop(0L, "노트북", "SAMSUNG", "MODEL", null, null, "hash", "1234"));

    Product product = Product.laptop(1L, "노트북", "SAMSUNG", "MODEL", null, null, "hash", "1234");

    assertThrows(
        IllegalArgumentException.class,
        () -> new LaptopDetail(product, null, 0, null, null, null, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LaptopDetail(product, null, null, null, null, null, null, (short) 101, null));
  }

  @Test
  void updatesOnlyEditableProductInformationAndLaptopSpecification() {
    Product product =
        Product.laptop(
            1L,
            "업무용 노트북",
            "SAMSUNG",
            "NT960X",
            LocalDate.of(2026, 1, 10),
            null,
            "fingerprint",
            "1234");
    LaptopDetail detail =
        new LaptopDetail(product, "기존 CPU", 16, 512, StorageType.SSD, null, null, null, null);

    product.updateEditableInformation("NT960X-PRO", LocalDate.of(2026, 2, 1));
    detail.updateSpecification(
        "새 CPU", 32, 1024, StorageType.SSD, "새 GPU", "새 화면", (short) 95, "새 OS");

    assertEquals("NT960X-PRO", product.getModelName());
    assertEquals(LocalDate.of(2026, 2, 1), product.getPurchaseDate());
    assertEquals("SAMSUNG", product.getManufacturer());
    assertEquals("1234", product.getSerialLast4());
    assertEquals("새 CPU", detail.getCpuSpec());
    assertEquals(32, detail.getRamGb());
  }
}
