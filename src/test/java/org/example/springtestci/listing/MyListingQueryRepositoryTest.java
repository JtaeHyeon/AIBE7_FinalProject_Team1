package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.repository.MyListingQueryRepository;
import org.example.springtestci.listing.service.MyListingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MyListingQueryRepositoryTest {
  private SingleConnectionDataSource source;
  private JdbcTemplate jdbc;
  private MyListingService service;

  @BeforeEach
  void start() {
    source =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:my_listings_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute(
        "CREATE TABLE products(id BIGINT PRIMARY KEY,owner_id BIGINT,archived_at TIMESTAMP)");
    jdbc.execute(
        "CREATE TABLE listings(id BIGINT PRIMARY KEY,product_id BIGINT,seller_id BIGINT,title VARCHAR(100),price DECIMAL(14),status VARCHAR(20),published_at TIMESTAMP)");
    jdbc.execute(
        "CREATE TABLE listing_images(listing_id BIGINT,sort_order INT,product_image_id BIGINT,is_primary BOOLEAN)");
    jdbc.execute("CREATE TABLE product_images(id BIGINT,product_id BIGINT,deleted_at TIMESTAMP)");
    jdbc.update("INSERT INTO products VALUES(101,1,NULL),(102,1,NULL),(103,1,NULL),(104,2,NULL)");
    jdbc.update(
        "INSERT INTO listings VALUES(11,101,1,'active',1000,'ACTIVE','2026-10-30 01:00:00'),(12,102,1,'reserved',2000,'RESERVED','2026-10-30 02:00:00'),(13,103,1,'sold',3000,'SOLD','2026-10-30 03:00:00'),(14,103,1,'cancelled',4000,'CANCELLED','2026-10-30 03:00:00'),(15,104,2,'foreign',5000,'ACTIVE','2026-10-30 04:00:00')");
    jdbc.update("INSERT INTO product_images VALUES(301,101,NULL),(302,102,NULL),(303,103,NULL)");
    jdbc.update(
        "INSERT INTO listing_images VALUES(11,0,301,TRUE),(12,0,302,TRUE),(13,0,303,TRUE),(14,0,303,TRUE)");
    service = new MyListingService(new MyListingQueryRepository(jdbc));
  }

  @AfterEach
  void stop() {
    source.destroy();
  }

  @Test
  void pagesOnlyOriginalSellersListingsIncludingReservedAndClosed() {
    var first = service.list(1, null, 0, 2);
    assertEquals(4, first.page().totalElements());
    assertTrue(first.page().hasNext());
    assertEquals(List.of(14L, 13L), first.items().stream().map(item -> item.listingId()).toList());
    assertTrue(
        first.items().stream().noneMatch(item -> item.canManage() || item.thumbnailUrl() != null));
    var next = service.list(1, null, 1, 2);
    assertEquals(List.of(12L, 11L), next.items().stream().map(item -> item.listingId()).toList());
    assertFalse(next.page().hasNext());
    assertTrue(next.items().stream().allMatch(item -> item.canManage()));
    assertEquals("/api/v1/products/102/images/302", next.items().get(0).thumbnailUrl());
    assertEquals(15, service.list(2, null, 0, 20).items().get(0).listingId());
    assertEquals(0, service.list(3, null, 0, 20).page().totalElements());
  }

  @Test
  void eachStatusFiltersTheTotalAndRowsWithinTheSeller() {
    for (var status : ListingStatus.values()) {
      var result = service.list(1, status, 0, 20);
      assertEquals(1, result.page().totalElements());
      assertEquals(status, result.items().get(0).status());
    }
  }

  @Test
  void transferredArchivedAndDeletedPhotosNeverGiveManagementOrAnotherOwnersImage() {
    jdbc.update("UPDATE products SET owner_id=2 WHERE id=101");
    jdbc.update("UPDATE products SET archived_at=CURRENT_TIMESTAMP WHERE id=102");
    var result = service.list(1, null, 0, 20);
    assertEquals(4, result.page().totalElements());
    assertTrue(
        result.items().stream().noneMatch(item -> item.canManage() || item.thumbnailUrl() != null));
    jdbc.update("UPDATE products SET owner_id=1 WHERE id=101");
    jdbc.update("UPDATE product_images SET deleted_at=CURRENT_TIMESTAMP WHERE id=301");
    var active = service.list(1, ListingStatus.ACTIVE, 0, 20).items().get(0);
    assertTrue(active.canManage());
    assertNull(active.thumbnailUrl());
  }

  @Test
  void rejectsInvalidIdentityAndUnboundedPages() {
    assertThrows(CommonException.class, () -> service.list(0, null, 0, 20));
    assertThrows(CommonException.class, () -> service.list(1, null, -1, 20));
    assertThrows(CommonException.class, () -> service.list(1, null, 0, 0));
    assertThrows(CommonException.class, () -> service.list(1, null, 0, 101));
  }
}
