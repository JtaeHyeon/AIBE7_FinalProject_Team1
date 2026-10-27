package org.example.springtestci.listing.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.example.springtestci.listing.dto.ListingSearchRequest;
import org.example.springtestci.listing.dto.ListingSummaryResponse;
import org.example.springtestci.product.service.SerialNumberProtector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ListingQueryRepository {
  private static final String FROM =
      """
      FROM listings l
      JOIN products p ON p.id = l.product_id
      LEFT JOIN laptop_details d ON d.product_id = p.id
      """;
  private static final String AVAILABLE =
      """
      WHERE l.status = 'ACTIVE' AND l.seller_id = p.owner_id
        AND p.archived_at IS NULL AND p.category = 'LAPTOP'
      """;
  private final JdbcTemplate jdbc;

  public ListingQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 공개 조건·필터를 적용한 뒤 페이지를 나누며, 본문·사진 컬렉션을 별도로 읽지 않는다. */
  public Page<ListingSummaryResponse> findActive(ListingSearchRequest filter) {
    var where = new StringBuilder(AVAILABLE);
    var values = new ArrayList<Object>();
    if (filter.manufacturer() != null)
      condition(
          where,
          values,
          "p.manufacturer_normalized = ?",
          SerialNumberProtector.normalizeManufacturer(filter.manufacturer()));
    if (filter.minPrice() != null) condition(where, values, "l.price >= ?", filter.minPrice());
    if (filter.maxPrice() != null) condition(where, values, "l.price <= ?", filter.maxPrice());
    if (filter.cpu() != null)
      condition(where, values, "LOWER(d.cpu_spec) LIKE ? ESCAPE '!'", contains(filter.cpu()));
    if (filter.gpu() != null)
      condition(where, values, "LOWER(d.gpu_spec) LIKE ? ESCAPE '!'", contains(filter.gpu()));
    if (filter.minRamGb() != null) condition(where, values, "d.ram_gb >= ?", filter.minRamGb());
    if (filter.minStorageGb() != null)
      condition(where, values, "d.storage_gb >= ?", filter.minStorageGb());
    if (filter.storageType() != null)
      condition(where, values, "d.storage_type = ?", filter.storageType());
    if (filter.os() != null)
      condition(where, values, "LOWER(d.os_name) LIKE ? ESCAPE '!'", contains(filter.os()));
    if (filter.minConditionGrade() != null)
      condition(where, values, "l.condition_grade >= ?", filter.minConditionGrade());

    long total =
        jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, values.toArray());
    String order =
        switch (filter.sort()) {
          case LATEST -> "l.published_at DESC, l.id DESC";
          case PRICE_ASC -> "l.price ASC, l.id DESC";
          case PRICE_DESC -> "l.price DESC, l.id DESC";
        };
    var pageable = PageRequest.of(filter.page(), filter.size());
    var pageValues = new ArrayList<>(values);
    pageValues.add(pageable.getPageSize());
    pageValues.add(pageable.getOffset());
    var items =
        jdbc.query(
            """
        SELECT l.id, l.title, l.price, l.condition_grade, l.published_at,
               p.manufacturer, p.model_name, d.cpu_spec, d.gpu_spec,
               d.ram_gb, d.storage_gb, d.storage_type, d.os_name,
               pi.id AS thumbnail_image_id
        """
                + FROM
                + """
        LEFT JOIN listing_images li ON li.listing_id = l.id AND li.sort_order = 0 AND li.is_primary = TRUE
        LEFT JOIN product_images pi ON pi.id = li.product_image_id
          AND pi.product_id = p.id AND pi.deleted_at IS NULL
        """
                + where
                + " ORDER BY "
                + order
                + " LIMIT ? OFFSET ?",
            (row, index) ->
                new ListingSummaryResponse(
                    row.getLong("id"),
                    row.getString("title"),
                    row.getBigDecimal("price"),
                    row.getInt("condition_grade"),
                    row.getTimestamp("published_at").toInstant(),
                    row.getString("manufacturer"),
                    row.getString("model_name"),
                    new ListingSummaryResponse.Specifications(
                        row.getString("cpu_spec"), row.getString("gpu_spec"),
                        row.getObject("ram_gb", Integer.class),
                            row.getObject("storage_gb", Integer.class),
                        row.getString("storage_type"), row.getString("os_name")),
                    row.getObject("thumbnail_image_id") == null
                        ? null
                        : "/api/v1/listings/" + row.getLong("id") + "/thumbnail"),
            pageValues.toArray());
    return new PageImpl<>(items, pageable, total);
  }

  public Optional<Long> findPrimaryImageId(long listingId, long productId) {
    return jdbc
        .queryForList(
            """
        SELECT pi.id FROM listing_images li
        JOIN product_images pi ON pi.id = li.product_image_id
        WHERE li.listing_id = ? AND li.sort_order = 0 AND li.is_primary = TRUE
          AND pi.product_id = ? AND pi.deleted_at IS NULL
        """,
            Long.class,
            listingId,
            productId)
        .stream()
        .findFirst();
  }

  private void condition(StringBuilder where, List<Object> values, String clause, Object value) {
    where.append(" AND ").append(clause);
    values.add(value);
  }

  private String contains(String value) {
    return "%"
        + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }
}
