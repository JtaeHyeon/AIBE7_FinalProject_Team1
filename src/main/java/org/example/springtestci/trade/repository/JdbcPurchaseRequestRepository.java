package org.example.springtestci.trade.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    prefix = "app.features.trade-purchase-requests",
    name = "enabled",
    havingValue = "true")
/** 구매 요청 도메인 모델과 `purchase_requests` 테이블을 명시적으로 매핑하는 JDBC 어댑터다. */
public class JdbcPurchaseRequestRepository implements PurchaseRequestRepository {

  private static final String SELECT_REQUEST =
      "select id, room_id, listing_id, request_status, transaction_status, seller_confirmed_version, buyer_confirmed_version, cancelled_by_id, cancel_reason, cancelled_at from purchase_requests";

  private static final RowMapper<PurchaseRequest> ROW_MAPPER =
      JdbcPurchaseRequestRepository::mapRequest;

  private final JdbcTemplate jdbcTemplate;

  public JdbcPurchaseRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<PurchaseRequest> findById(long requestId) {
    return queryOne(SELECT_REQUEST + " where id = ?", requestId);
  }

  @Override
  public Optional<PurchaseRequest> findByIdForUpdate(long requestId) {
    return queryOne(SELECT_REQUEST + " where id = ? for update", requestId);
  }

  @Override
  public Optional<PurchaseRequest> findPendingByRoomId(long roomId) {
    return queryOne(SELECT_REQUEST + " where room_id = ? and request_status = 'PENDING'", roomId);
  }

  @Override
  public Optional<PurchaseRequest> findReservedByListingId(long listingId) {
    return queryOne(
        SELECT_REQUEST + " where listing_id = ? and transaction_status = 'RESERVED'", listingId);
  }

  @Override
  public List<PurchaseRequest> findByListingIdForUpdate(long listingId) {
    return jdbcTemplate.query(
        SELECT_REQUEST + " where listing_id = ? order by id for update", ROW_MAPPER, listingId);
  }

  @Override
  public PurchaseRequest save(PurchaseRequest request) {
    Long id =
        jdbcTemplate.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status, transaction_status) values (?, ?, ?, ?) returning id",
            Long.class,
            request.roomId(),
            request.listingId(),
            request.requestStatus().name(),
            request.transactionStatus() == null ? null : request.transactionStatus().name());
    return PurchaseRequest.restore(
        id,
        request.roomId(),
        request.listingId(),
        request.requestStatus(),
        request.transactionStatus(),
        request.sellerConfirmedVersion(),
        request.buyerConfirmedVersion(),
        request.cancelledById(),
        request.cancelReason(),
        request.cancelledAt());
  }

  @Override
  public void update(PurchaseRequest request) {
    int updated =
        jdbcTemplate.update(
            "update purchase_requests set request_status = ?, transaction_status = ? where id = ?",
            request.requestStatus().name(),
            request.transactionStatus() == null ? null : request.transactionStatus().name(),
            request.id());
    if (updated != 1) {
      throw new IllegalStateException("Purchase request update affected an unexpected row count");
    }
  }

  @Override
  public void cancelReserved(PurchaseRequest request) {
    int updated =
        jdbcTemplate.update(
            "update purchase_requests set request_status = 'CANCELLED', transaction_status = 'CANCELLED', seller_confirmed_version = null, buyer_confirmed_version = null, cancelled_by_id = ?, cancel_reason = ?, cancelled_at = ? where id = ? and request_status = 'ACCEPTED' and transaction_status = 'RESERVED'",
            request.cancelledById(),
            request.cancelReason().name(),
            Timestamp.from(request.cancelledAt()),
            request.id());
    if (updated != 1) {
      throw new org.example.springtestci.common.error.CommonException(
          org.example.springtestci.common.error.ErrorCode.TRADE_CONFLICT);
    }
  }

  @Override
  public void clearConfirmedVersions(long requestId) {
    int updated =
        jdbcTemplate.update(
            "update purchase_requests set seller_confirmed_version = null, buyer_confirmed_version = null where id = ? and request_status = 'ACCEPTED' and transaction_status = 'RESERVED'",
            requestId);
    if (updated != 1) {
      throw new org.example.springtestci.common.error.CommonException(
          org.example.springtestci.common.error.ErrorCode.TRADE_CONFLICT);
    }
  }

  @Override
  public PageResult findAll(
      long actorId,
      PurchaseRequestDirection direction,
      PurchaseRequestStatus status,
      Long roomId,
      int page,
      int size) {
    String participantColumn =
        direction == PurchaseRequestDirection.SENT ? "room.inquirer_id" : "room.seller_id";
    StringBuilder where = new StringBuilder(" where " + participantColumn + " = ?");
    List<Object> parameters = new ArrayList<>();
    parameters.add(actorId);
    if (status != null) {
      where.append(" and request.request_status = ?");
      parameters.add(status.name());
    }
    if (roomId != null) {
      where.append(" and request.room_id = ?");
      parameters.add(roomId);
    }

    String from =
        " from purchase_requests request join chat_rooms room on room.id = request.room_id";
    Long total =
        jdbcTemplate.queryForObject(
            "select count(*)" + from + where, Long.class, parameters.toArray());
    List<Object> pageParameters = new ArrayList<>(parameters);
    pageParameters.add(size);
    pageParameters.add((long) page * size);
    List<PurchaseRequest> items =
        jdbcTemplate.query(
            "select request.id, request.room_id, request.listing_id, request.request_status, request.transaction_status, request.seller_confirmed_version, request.buyer_confirmed_version, request.cancelled_by_id, request.cancel_reason, request.cancelled_at"
                + from
                + where
                + " order by request.id desc limit ? offset ?",
            ROW_MAPPER,
            pageParameters.toArray());
    return new PageResult(items, total == null ? 0 : total);
  }

  private Optional<PurchaseRequest> queryOne(String sql, Object... parameters) {
    return jdbcTemplate.query(sql, ROW_MAPPER, parameters).stream().findFirst();
  }

  private static PurchaseRequest mapRequest(ResultSet resultSet, int rowNumber)
      throws SQLException {
    String transactionStatus = resultSet.getString("transaction_status");
    String cancelReason = resultSet.getString("cancel_reason");
    Timestamp cancelledAt = resultSet.getTimestamp("cancelled_at");
    return PurchaseRequest.restore(
        resultSet.getLong("id"),
        resultSet.getLong("room_id"),
        resultSet.getLong("listing_id"),
        PurchaseRequestStatus.valueOf(resultSet.getString("request_status")),
        transactionStatus == null ? null : TransactionStatus.valueOf(transactionStatus),
        resultSet.getObject("seller_confirmed_version", Long.class),
        resultSet.getObject("buyer_confirmed_version", Long.class),
        resultSet.getObject("cancelled_by_id", Long.class),
        cancelReason == null ? null : TradeCancellationReason.valueOf(cancelReason),
        cancelledAt == null ? null : cancelledAt.toInstant());
  }
}
