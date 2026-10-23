package org.example.springtestci.ownership.repository;

import java.sql.SQLException;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    prefix = "app.features.trade-purchase-requests",
    name = "enabled",
    havingValue = "true")
public class JdbcInternalTransferPendingWriter implements InternalTransferPendingWriter {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String PENDING_PRODUCT_CONSTRAINT = "uq_ownership_transfers_pending_product";

  private final JdbcTemplate jdbcTemplate;

  public JdbcInternalTransferPendingWriter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void createPending(Command command) {
    int inserted;
    try {
      inserted =
          jdbcTemplate.update(
              "insert into ownership_transfers "
                  + "(product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) "
                  + "values (?, ?, ?, ?, ?, ?)",
              command.productId(),
              command.purchaseRequestId(),
              command.fromUserId(),
              command.toUserId(),
              command.transferType(),
              command.status());
    } catch (DataAccessException exception) {
      throw translateConflict(exception);
    }
    if (inserted != 1) {
      throw new IllegalStateException("Ownership transfer insert affected an unexpected row count");
    }
  }

  private RuntimeException translateConflict(DataAccessException exception) {
    Throwable cause = exception.getMostSpecificCause();
    if (cause instanceof SQLException sqlException
        && UNIQUE_VIOLATION.equals(sqlException.getSQLState())
        && sqlException.getMessage() != null
        && sqlException.getMessage().contains(PENDING_PRODUCT_CONSTRAINT)) {
      return new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    return exception;
  }
}
