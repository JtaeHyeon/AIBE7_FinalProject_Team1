package org.example.springtestci.admin.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import org.example.springtestci.admin.dto.AdminDtos.*;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관리 조회 전용 투영이다. 채팅에서는 당사자 ID만 읽으며 문서·메시지 테이블은 조회하지 않는다. */
@Repository
public class AdminReadRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public AdminReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // 기존 거래에 시각 컬럼이 없어 해당 요청의 성공 감사 기록에서 예약·완료 시각을 읽는다.
  private static final String TRADES =
      """
      SELECT pr.id, l.product_id, pr.listing_id, cr.seller_id, cr.inquirer_id AS buyer_id,
             p.title AS product_title, p.manufacturer AS product_manufacturer, p.model_name AS product_model_name,
             l.title AS listing_title, seller.nickname AS seller_nickname, seller.email AS seller_email,
             buyer.nickname AS buyer_nickname, buyer.email AS buyer_email,
             canceller.nickname AS canceller_nickname, canceller.email AS canceller_email,
             pr.request_status, pr.transaction_status, pr.cancelled_at, pr.cancelled_by_id, pr.cancel_reason,
             (SELECT MIN(a.created_at) FROM audit_logs a WHERE a.product_id = l.product_id
                AND a.target_id = pr.id AND a.action = 'PURCHASE_REQUEST_ACCEPTED' AND a.result = 'SUCCESS') AS reserved_at,
             (SELECT MIN(a.created_at) FROM audit_logs a WHERE a.product_id = l.product_id
                AND a.target_id = pr.id AND a.action = 'TRADE_COMPLETED' AND a.result = 'SUCCESS') AS completed_at
        FROM purchase_requests pr JOIN listings l ON l.id = pr.listing_id
        JOIN chat_rooms cr ON cr.id = pr.room_id
        LEFT JOIN products p ON p.id = l.product_id
        LEFT JOIN users seller ON seller.id = cr.seller_id
        LEFT JOIN users buyer ON buyer.id = cr.inquirer_id
        LEFT JOIN users canceller ON canceller.id = pr.cancelled_by_id
      """;
  private static final String AUDIT_COLUMNS =
      """
      a.id, a.product_id, a.actor_id, a.target_id, a.event_id, a.action, a.result, a.created_at,
      p.title AS product_title, p.manufacturer AS product_manufacturer, p.model_name AS product_model_name,
      actor.nickname AS actor_nickname, actor.email AS actor_email
      """;
  // LEFT JOIN으로 탈퇴한 처리 주체의 기록도 보존한다. 연락처 외 인증/문서/채팅 정보는 읽지 않는다.
  private static final String AUDIT_FROM =
      """
      FROM audit_logs a LEFT JOIN products p ON p.id = a.product_id
      LEFT JOIN users actor ON actor.id = a.actor_id
      """;
  private static final String AUDITS = "SELECT " + AUDIT_COLUMNS + AUDIT_FROM;

  public Page<Trade> trades(Filter filter) {
    var query = new Query("SELECT * FROM (" + TRADES + ") t WHERE 1=1");
    query.equal("product_id", "product", filter.productId());
    query.productSearch(filter.productQuery());
    query.search(
        filter.memberQuery(),
        "memberSearch",
        "seller_nickname",
        "seller_email",
        "buyer_nickname",
        "buyer_email",
        "canceller_nickname",
        "canceller_email");
    if (filter.actorId() != null) {
      query.sql.append(
          " AND (seller_id = :actor OR buyer_id = :actor OR cancelled_by_id = :actor)");
      query.params.put("actor", filter.actorId());
    }
    if (filter.status() != null) {
      query.sql.append(" AND COALESCE(transaction_status, request_status) = :status");
      query.params.put("status", filter.status());
    }
    query.dates("reserved_at", filter);
    long total = count(query);
    query.page(filter, "id DESC");
    return new Page<>(
        jdbc.query(query.sql.toString(), query.params, this::mapTrade),
        filter.page(),
        filter.size(),
        total);
  }

  public Trade trade(long id) {
    return jdbc
        .query(
            "SELECT * FROM (" + TRADES + ") t WHERE id = :id",
            java.util.Map.of("id", id),
            this::mapTrade)
        .stream()
        .findFirst()
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  public Page<Audit> audits(Filter filter) {
    var query = new Query("SELECT * FROM (" + AUDITS + ") t WHERE 1=1");
    query.equal("product_id", "product", filter.productId());
    query.equal("actor_id", "actor", filter.actorId());
    query.equal("action", "action", filter.action());
    query.productSearch(filter.productQuery());
    query.search(filter.memberQuery(), "memberSearch", "actor_nickname", "actor_email");
    query.dates("created_at", filter);
    long total = count(query);
    query.page(filter, "created_at DESC, id DESC");
    return new Page<>(
        jdbc.query(query.sql.toString(), query.params, this::mapAudit),
        filter.page(),
        filter.size(),
        total);
  }

  public AuditSource audit(long id) {
    return jdbc
        .query(
            "SELECT "
                + AUDIT_COLUMNS
                + ", a.reason, a.before_facts, a.after_facts "
                + AUDIT_FROM
                + " WHERE a.id = :id",
            java.util.Map.of("id", id),
            (row, index) ->
                new AuditSource(
                    mapAudit(row, index),
                    row.getString("reason"),
                    row.getString("before_facts"),
                    row.getString("after_facts")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private long count(Query query) {
    Long value =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" + query.sql + ") counted", query.params, Long.class);
    return value == null ? 0 : value;
  }

  private Trade mapTrade(ResultSet r, int index) throws SQLException {
    return new Trade(
        r.getLong("id"),
        r.getLong("product_id"),
        r.getLong("listing_id"),
        r.getLong("seller_id"),
        r.getLong("buyer_id"),
        r.getString("request_status"),
        r.getString("transaction_status"),
        time(r, "reserved_at"),
        time(r, "completed_at"),
        time(r, "cancelled_at"),
        (Long) r.getObject("cancelled_by_id"),
        r.getString("cancel_reason"),
        product(r),
        r.getString("listing_title"),
        member(r, "seller_id", "seller"),
        member(r, "buyer_id", "buyer"),
        member(r, "cancelled_by_id", "canceller"));
  }

  private Audit mapAudit(ResultSet r, int index) throws SQLException {
    return new Audit(
        r.getLong("id"),
        r.getLong("product_id"),
        (Long) r.getObject("actor_id"),
        r.getLong("target_id"),
        (Long) r.getObject("event_id"),
        r.getString("action"),
        r.getString("result"),
        time(r, "created_at"),
        product(r),
        member(r, "actor_id", "actor"));
  }

  private ProductSummary product(ResultSet r) throws SQLException {
    return new ProductSummary(
        r.getLong("product_id"),
        r.getString("product_title"),
        r.getString("product_manufacturer"),
        r.getString("product_model_name"));
  }

  private MemberSummary member(ResultSet r, String idColumn, String prefix) throws SQLException {
    Number id = (Number) r.getObject(idColumn);
    return id == null
        ? null
        : new MemberSummary(
            id.longValue(), r.getString(prefix + "_nickname"), r.getString(prefix + "_email"));
  }

  private static OffsetDateTime time(ResultSet r, String name) throws SQLException {
    return r.getObject(name, OffsetDateTime.class);
  }

  /** 저장용 원문은 서비스의 허용 필드 선별을 거친 뒤에만 API 응답으로 변환한다. */
  public record AuditSource(Audit record, String reason, String before, String after) {}

  private static final class Query {
    private final StringBuilder sql;
    private final HashMap<String, Object> params = new HashMap<>();

    private Query(String sql) {
      this.sql = new StringBuilder(sql);
    }

    private void equal(String column, String name, Object value) {
      if (value != null) {
        sql.append(" AND ").append(column).append(" = :").append(name);
        params.put(name, value);
      }
    }

    private void dates(String column, Filter filter) {
      if (filter.from() != null) {
        sql.append(" AND ").append(column).append(" >= :from");
        params.put("from", filter.from().atStartOfDay().atOffset(ZoneOffset.UTC));
      }
      if (filter.to() != null) {
        sql.append(" AND ").append(column).append(" < :until");
        params.put("until", filter.to().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
      }
    }

    private void productSearch(String value) {
      search(value, "productSearch", "product_title", "product_manufacturer", "product_model_name");
    }

    /** 검색어의 SQL 와일드카드도 문자로 취급하며, 허용된 고정 열만 검색한다. */
    private void search(String value, String name, String... columns) {
      if (value == null) return;
      String pattern =
          "%"
              + value
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      params.put(name, pattern);
      sql.append(" AND (");
      for (int i = 0; i < columns.length; i++) {
        if (i > 0) sql.append(" OR ");
        sql.append("LOWER(COALESCE(")
            .append(columns[i])
            .append(", '')) LIKE :")
            .append(name)
            .append(" ESCAPE '!'");
      }
      sql.append(")");
    }

    private void page(Filter filter, String order) {
      sql.append(" ORDER BY ").append(order).append(" LIMIT :size OFFSET :offset");
      params.put("size", filter.size());
      params.put("offset", filter.offset());
    }
  }
}
