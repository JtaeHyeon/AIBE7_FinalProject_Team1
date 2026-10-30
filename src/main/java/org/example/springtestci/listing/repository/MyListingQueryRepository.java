package org.example.springtestci.listing.repository;

import java.util.ArrayList;
import org.example.springtestci.listing.dto.MyListingPageResponse;
import org.example.springtestci.listing.entity.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 인증된 판매자의 글만 페이지 단위로 조회한다. 공개 마켓의 ACTIVE 조건과 분리한다. */
@Repository
public class MyListingQueryRepository {
  private final JdbcTemplate jdbc;

  public MyListingQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Page<MyListingPageResponse.Item> findBySeller(
      long sellerId, ListingStatus status, int page, int size) {
    String where = " WHERE l.seller_id = ?" + (status == null ? "" : " AND l.status = ?");
    var values = new ArrayList<Object>();
    values.add(sellerId);
    if (status != null) values.add(status.name());
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM listings l" + where, Long.class, values.toArray());
    var pageable = PageRequest.of(page, size);
    values.add(size);
    values.add(pageable.getOffset());
    var items =
        jdbc.query(
            """
        SELECT l.id,l.title,l.price,l.status,l.published_at,l.product_id,
          (p.owner_id = l.seller_id AND p.archived_at IS NULL) AS owns_product,
          pi.id AS image_id
        FROM listings l
        JOIN products p ON p.id = l.product_id
        LEFT JOIN listing_images li ON li.listing_id = l.id AND li.sort_order = 0 AND li.is_primary = TRUE
        LEFT JOIN product_images pi ON pi.id = li.product_image_id AND pi.product_id = p.id
          AND pi.deleted_at IS NULL AND p.owner_id = l.seller_id AND p.archived_at IS NULL
          AND l.status IN ('ACTIVE','RESERVED')
        """
                + where
                + " ORDER BY l.published_at DESC,l.id DESC LIMIT ? OFFSET ?",
            (row, index) -> {
              var state = ListingStatus.valueOf(row.getString("status"));
              // 소유권 이전 후에는 새 소유자의 사진이나 관리 동선을 과거 판매자에게 제공하지 않는다.
              boolean manageable = row.getBoolean("owns_product") && state.isPubliclyReadable();
              return new MyListingPageResponse.Item(
                  row.getLong("id"),
                  row.getString("title"),
                  row.getBigDecimal("price"),
                  state,
                  row.getTimestamp("published_at").toInstant(),
                  row.getObject("image_id") == null
                      ? null
                      : "/api/v1/products/"
                          + row.getLong("product_id")
                          + "/images/"
                          + row.getLong("image_id"),
                  manageable);
            },
            values.toArray());
    return new PageImpl<>(items, pageable, total);
  }
}
