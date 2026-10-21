package org.example.springtestci.trade.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.trade.dto.PurchaseRequestDirection;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
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
public class JdbcPurchaseRequestRepository implements PurchaseRequestRepository {

  private static final RowMapper<PurchaseRequest> ROW_MAPPER =
      JdbcPurchaseRequestRepository::mapRequest;

  private final JdbcTemplate jdbcTemplate;

  public JdbcPurchaseRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<PurchaseRequest> findById(long requestId) {
    return queryOne(
        "select id, room_id, listing_id, request_status, transaction_status from purchase_requests where id = ?",
        requestId);
  }

  @Override
  public Optional<PurchaseRequest> findByIdForUpdate(long requestId) {
    return queryOne(
        "select id, room_id, listing_id, request_status, transaction_status from purchase_requests where id = ? for update",
        requestId);
  }

  @Override
  public Optional<PurchaseRequest> findPendingByRoomId(long roomId) {
    return queryOne(
        "select id, room_id, listing_id, request_status, transaction_status from purchase_requests where room_id = ? and request_status = 'PENDING'",
        roomId);
  }

  @Override
  public Optional<PurchaseRequest> findReservedByListingId(long listingId) {
    return queryOne(
        "select id, room_id, listing_id, request_status, transaction_status from purchase_requests where listing_id = ? and transaction_status = 'RESERVED'",
        listingId);
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
        request.transactionStatus());
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
            "select request.id, request.room_id, request.listing_id, request.request_status, request.transaction_status"
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
    return PurchaseRequest.restore(
        resultSet.getLong("id"),
        resultSet.getLong("room_id"),
        resultSet.getLong("listing_id"),
        PurchaseRequestStatus.valueOf(resultSet.getString("request_status")),
        transactionStatus == null ? null : TransactionStatus.valueOf(transactionStatus));
  }
}
