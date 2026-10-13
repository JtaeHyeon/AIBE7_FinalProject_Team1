package org.example.springtestci.listing.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read adapter for the shared PROD schema. Product writes must lock products first. */
@Repository
public class DraftProductRepository {

  private static final String PRODUCT_COLUMNS =
      """
      SELECT p.id, p.title, p.manufacturer, p.model_name, p.purchase_date,
             p.warranty_expires_at, l.cpu_spec, l.gpu_spec, l.ram_gb,
             l.storage_gb, l.storage_type, l.display_spec, l.battery_health_percent, l.os_name
        FROM products p LEFT JOIN laptop_details l ON l.product_id = p.id
      """;
  private final JdbcTemplate jdbc;

  public DraftProductRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DraftProductResponse> findOwnedProducts(long userId) {
    try {
      return jdbc.query(
          PRODUCT_COLUMNS
              + " WHERE p.owner_id = ? AND p.archived_at IS NULL"
              + " AND p.category = 'LAPTOP' ORDER BY p.created_at DESC, p.id DESC",
          this::product,
          userId);
    } catch (BadSqlGrammarException exception) {
      throw new CommonException(ErrorCode.DRAFT_PRODUCT_UNAVAILABLE);
    }
  }

  public void requireOwnership(long productId, long userId, boolean lock) {
    try {
      List<Long> matches =
          jdbc.queryForList(
              """
          SELECT id FROM products WHERE id = ? AND owner_id = ?
          AND archived_at IS NULL AND category = 'LAPTOP'
          """
                  + (lock ? " FOR UPDATE" : ""),
              Long.class,
              productId,
              userId);
      if (matches.isEmpty()) throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    } catch (BadSqlGrammarException exception) {
      throw new CommonException(ErrorCode.DRAFT_PRODUCT_UNAVAILABLE);
    }
  }

  public DraftProductResponse composition(long productId, long userId) {
    requireOwnership(productId, userId, false);
    DraftProductResponse product =
        jdbc.query(PRODUCT_COLUMNS + " WHERE p.id = ?", this::product, productId).get(0);
    List<DraftProductResponse.Image> photos =
        images(productId, false).stream()
            .map(
                image ->
                    new DraftProductResponse.Image(
                        image.imageId(),
                        "/api/v1/products/"
                            + productId
                            + "/listing-draft/images/"
                            + image.imageId()))
            .toList();
    return new DraftProductResponse(
        product.productId(),
        product.title(),
        product.manufacturer(),
        product.modelName(),
        product.purchaseDate(),
        product.warrantyExpiresAt(),
        product.specifications(),
        photos);
  }

  public List<ProductImage> images(long productId, boolean lock) {
    return jdbc.query(
        """
        SELECT id, storage_key, content_type FROM product_images
        WHERE product_id = ? AND deleted_at IS NULL
        AND content_type IN ('image/png', 'image/jpeg', 'image/webp')
        ORDER BY sort_order, id
        """
            + (lock ? " FOR UPDATE" : ""),
        (row, index) ->
            new ProductImage(
                row.getLong("id"), row.getString("storage_key"), row.getString("content_type")),
        productId);
  }

  private DraftProductResponse product(ResultSet row, int index) throws SQLException {
    return new DraftProductResponse(
        row.getLong("id"),
        row.getString("title"),
        row.getString("manufacturer"),
        row.getString("model_name"),
        row.getObject("purchase_date", java.time.LocalDate.class),
        row.getObject("warranty_expires_at", java.time.LocalDate.class),
        new DraftProductResponse.Specifications(
            row.getString("cpu_spec"),
            row.getString("gpu_spec"),
            row.getObject("ram_gb", Integer.class),
            row.getObject("storage_gb", Integer.class),
            row.getString("storage_type"),
            row.getString("display_spec"),
            row.getObject("battery_health_percent", Integer.class),
            row.getString("os_name")),
        List.of());
  }

  public record ProductImage(long imageId, String storageKey, String contentType) {}
}
