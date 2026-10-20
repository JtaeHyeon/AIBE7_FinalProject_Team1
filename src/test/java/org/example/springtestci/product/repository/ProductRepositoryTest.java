package org.example.springtestci.product.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.springtestci.product.dto.CreateProductRequest;
import org.example.springtestci.product.dto.ProductResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 전체 마이그레이션을 빈 DB에 적용한 뒤, 저장소의 SQL이 실제 스키마에서 동작하는지 확인한다. */
class ProductRepositoryTest {

  private static final String URL =
      "jdbc:h2:mem:product_repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

  private static ProductRepository repository;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    // H2 2.4는 제약을 만든 세션이 닫히면 문자열 비교 CHECK를 평가하지 못한다. 마이그레이션과 테스트가 한 연결을 같이 쓴다.
    SingleConnectionDataSource dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    repository = new ProductRepository(jdbc);
  }

  @Test
  void createdProductIsReadBackWithItsLaptopSpecifications() {
    long productId = insert(1L, "fp-read-back");

    ProductResponse product = repository.findOwned(productId, 1L).orElseThrow();

    assertEquals("MacBook Pro 14", product.title());
    assertEquals("Apple M3 Pro", product.specifications().cpu());
    assertEquals(18, product.specifications().ramGb());
    assertEquals("SSD", product.specifications().storageType());
    assertEquals(92, product.specifications().batteryHealthPercent());
  }

  @Test
  void aProductWithoutSpecificationsIsStillReadable() {
    long productId = repository.insertProduct(2L, request(null), "fp-without-specs", "ciphertext");
    repository.insertLaptopDetails(productId, null);

    ProductResponse product = repository.findOwned(productId, 2L).orElseThrow();

    assertEquals(null, product.specifications().cpu());
  }

  @Test
  void anotherUserCannotReadOrLockTheProduct() {
    long productId = insert(3L, "fp-other-user");

    assertTrue(repository.findOwned(productId, 99L).isEmpty());
    assertFalse(repository.lockOwned(productId, 99L));
    assertTrue(repository.lockOwned(productId, 3L));
  }

  @Test
  void findAllOwnedReturnsOnlyTheUsersProducts() {
    long mine = insert(4L, "fp-list-mine");
    insert(5L, "fp-list-theirs");

    List<ProductResponse> products = repository.findAllOwned(4L);

    assertEquals(1, products.size());
    assertEquals(mine, products.get(0).productId());
  }

  @Test
  void archivedProductsAreHidden() {
    long productId = insert(6L, "fp-archived");
    jdbc.update("UPDATE products SET archived_at = CURRENT_TIMESTAMP WHERE id = ?", productId);

    assertTrue(repository.findOwned(productId, 6L).isEmpty());
    assertTrue(repository.findAllOwned(6L).isEmpty());
    assertFalse(repository.lockOwned(productId, 6L));
  }

  @Test
  void theSameSerialFingerprintCannotBeRegisteredTwice() {
    insert(7L, "fp-duplicate");

    assertTrue(repository.existsBySerialFingerprint("fp-duplicate"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> repository.insertProduct(8L, request(null), "fp-duplicate", "ciphertext"));
  }

  @Test
  void imagesAreCountedAndOrderedAndSoftDeletedOnesAreIgnored() {
    long productId = insert(9L, "fp-images");

    long first = repository.insertImage(productId, "product-images/a.png", "image/png");
    long second = repository.insertImage(productId, "product-images/b.jpg", "image/jpeg");
    long deleted = repository.insertImage(productId, "product-images/c.png", "image/png");
    jdbc.update("UPDATE product_images SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deleted);

    assertEquals(2, repository.countImages(productId));
    assertEquals(List.of(first, second), repository.findImageIds(productId));
  }

  @Test
  void imageContentTypeIsLimitedToTheDraftAllowList() {
    long productId = insert(10L, "fp-content-type");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> repository.insertImage(productId, "product-images/x.gif", "image/gif"));
  }

  private long insert(long ownerId, String fingerprint) {
    CreateProductRequest request =
        request(
            new CreateProductRequest.Specifications(
                "Apple M3 Pro", null, 18, 512, "SSD", "14인치", 92, "macOS"));
    long productId = repository.insertProduct(ownerId, request, fingerprint, "ciphertext");
    repository.insertLaptopDetails(productId, request.specifications());
    return productId;
  }

  private static CreateProductRequest request(CreateProductRequest.Specifications specs) {
    return new CreateProductRequest(
        "MacBook Pro 14", "Apple", "MacBook Pro 14", null, null, "SERIAL1234", specs);
  }
}
