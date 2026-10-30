package org.example.springtestci.listing.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.service.ListingPublishConstraints;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 정식 거래·이전 테이블을 읽는 게시 검사다. 호출자가 소유권을 확인하고 제품을 잠근 트랜잭션에 참여한다. */
public class JdbcListingPublishConstraints implements ListingPublishConstraints {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public JdbcListingPublishConstraints(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** 제품에 연결된 RESERVED 거래와 아직 만료되지 않은 PENDING 이전을 검사한다. 원천 부재는 허용으로 보지 않는다. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Snapshot inspect(long productId) {
    if (productId <= 0) throw unavailable();
    try {
      // 미배포 테이블을 SELECT로 확인하면 PostgreSQL 트랜잭션이 깨지므로 메타데이터로 먼저 검사한다.
      boolean ready =
          Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) this::sourceReady));
      if (!ready) return new Snapshot(false, false, false);
      return jdbc.queryForObject(
          """
          SELECT EXISTS (
            SELECT 1 FROM purchase_requests r JOIN listings l ON l.id = r.listing_id
             WHERE l.product_id = ? AND r.transaction_status = 'RESERVED'
          ) AS ongoing_trade, EXISTS (
            SELECT 1 FROM ownership_transfers
             WHERE product_id = ? AND status = 'PENDING' AND expires_at >= ?
          ) AS pending_transfer
          """,
          (row, index) ->
              new Snapshot(
                  true, row.getBoolean("ongoing_trade"), row.getBoolean("pending_transfer")),
          productId,
          productId,
          Timestamp.from(clock.instant()));
    } catch (DataAccessException failure) {
      throw unavailable();
    }
  }

  private boolean sourceReady(Connection connection) throws SQLException {
    return hasColumns(connection, "listings", Set.of("id", "product_id"))
        && hasColumns(connection, "purchase_requests", Set.of("listing_id", "transaction_status"))
        && hasColumns(
            connection, "ownership_transfers", Set.of("product_id", "status", "expires_at"));
  }

  private boolean hasColumns(Connection connection, String table, Set<String> required)
      throws SQLException {
    var metadata = connection.getMetaData();
    String name = metadata.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
    Set<String> columns = new HashSet<>();
    try (var rows =
        metadata.getColumns(connection.getCatalog(), connection.getSchema(), name, null)) {
      while (rows.next()) {
        if (name.equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
          columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
      }
    }
    return columns.containsAll(required);
  }

  private static CommonException unavailable() {
    return new CommonException(ErrorCode.LISTING_PUBLISH_UNAVAILABLE);
  }
}
