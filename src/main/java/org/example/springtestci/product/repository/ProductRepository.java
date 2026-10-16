package org.example.springtestci.product.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.product.dto.CreateProductRequest;
import org.example.springtestci.product.dto.ProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * products, laptop_details, product_images 접근. 판매글 초안과 증빙 문서가 같은 테이블을 JDBC로 읽으므로 같은 방식을 쓰고, 쓰기는 이
 * 클래스에서만 한다.
 */
@Repository
public class ProductRepository {

  private static final String PRODUCT_SELECT =
      """
      SELECT p.id, p.title, p.manufacturer, p.model_name, p.purchase_date,
             p.warranty_expires_at, p.created_at, l.cpu_spec, l.gpu_spec, l.ram_gb,
             l.storage_gb, l.storage_type, l.display_spec, l.battery_health_percent, l.os_name
        FROM products p LEFT JOIN laptop_details l ON l.product_id = p.id
      """;

  private final JdbcTemplate jdbc;

  public ProductRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean existsBySerialFingerprint(String fingerprint) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM products WHERE serial_number_fingerprint = ?",
            Integer.class,
            fingerprint);
    return count != null && count > 0;
  }

  public long insertProduct(
      long ownerId, CreateProductRequest request, String fingerprint, String ciphertext) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO products (category, manufacturer, model_name, purchase_date,
                      serial_number_fingerprint, title, warranty_expires_at, owner_id,
                      serial_number_ciphertext)
                  VALUES ('LAPTOP', ?, ?, ?, ?, ?, ?, ?, ?)
                  """,
                  new String[] {"id"});
          statement.setString(1, request.manufacturer());
          statement.setString(2, request.modelName());
          statement.setObject(3, request.purchaseDate());
          statement.setString(4, fingerprint);
          statement.setString(5, request.title());
          statement.setObject(6, request.warrantyExpiresAt());
          statement.setLong(7, ownerId);
          statement.setString(8, ciphertext);
          return statement;
        },
        keys);
    return keys.getKey().longValue();
  }

  public void insertLaptopDetails(long productId, CreateProductRequest.Specifications specs) {
    jdbc.update(
        """
        INSERT INTO laptop_details (product_id, cpu_spec, ram_gb, storage_gb, storage_type,
            gpu_spec, os_name, display_spec, battery_health_percent)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        productId,
        specs == null ? null : specs.cpu(),
        specs == null ? null : specs.ramGb(),
        specs == null ? null : specs.storageGb(),
        specs == null ? null : specs.storageType(),
        specs == null ? null : specs.gpu(),
        specs == null ? null : specs.os(),
        specs == null ? null : specs.display(),
        specs == null ? null : specs.batteryHealthPercent());
  }

  public Optional<ProductResponse> findOwned(long productId, long ownerId) {
    return jdbc
        .query(
            PRODUCT_SELECT + " WHERE p.id = ? AND p.owner_id = ? AND p.archived_at IS NULL",
            this::product,
            productId,
            ownerId)
        .stream()
        .findFirst();
  }

  public List<ProductResponse> findAllOwned(long ownerId) {
    return jdbc.query(
        PRODUCT_SELECT
            + " WHERE p.owner_id = ? AND p.archived_at IS NULL"
            + " ORDER BY p.created_at DESC, p.id DESC",
        this::product,
        ownerId);
  }

  /** 이미지 개수 확인과 추가가 동시에 일어나도 한도를 넘지 않도록 제품 행을 잠근다. */
  public boolean lockOwned(long productId, long ownerId) {
    return !jdbc.queryForList(
            "SELECT id FROM products WHERE id = ? AND owner_id = ? AND archived_at IS NULL"
                + " FOR UPDATE",
            Long.class,
            productId,
            ownerId)
        .isEmpty();
  }

  public int countImages(long productId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_images WHERE product_id = ? AND deleted_at IS NULL",
            Integer.class,
            productId);
    return count == null ? 0 : count;
  }

  public long insertImage(long productId, String storageKey, String contentType) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO product_images (product_id, storage_key, content_type, sort_order)
                  VALUES (?, ?, ?, (SELECT COALESCE(MAX(sort_order) + 1, 0)
                                      FROM product_images WHERE product_id = ?))
                  """,
                  new String[] {"id"});
          statement.setLong(1, productId);
          statement.setString(2, storageKey);
          statement.setString(3, contentType);
          statement.setLong(4, productId);
          return statement;
        },
        keys);
    return keys.getKey().longValue();
  }

  public List<Long> findImageIds(long productId) {
    return jdbc.queryForList(
        "SELECT id FROM product_images WHERE product_id = ? AND deleted_at IS NULL"
            + " ORDER BY sort_order, id",
        Long.class,
        productId);
  }

  private ProductResponse product(ResultSet row, int index) throws SQLException {
    return new ProductResponse(
        row.getLong("id"),
        row.getString("title"),
        row.getString("manufacturer"),
        row.getString("model_name"),
        row.getObject("purchase_date", java.time.LocalDate.class),
        row.getObject("warranty_expires_at", java.time.LocalDate.class),
        new ProductResponse.Specifications(
            row.getString("cpu_spec"),
            row.getString("gpu_spec"),
            row.getObject("ram_gb", Integer.class),
            row.getObject("storage_gb", Integer.class),
            row.getString("storage_type"),
            row.getString("display_spec"),
            row.getObject("battery_health_percent", Integer.class),
            row.getString("os_name")),
        List.of(),
        row.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
