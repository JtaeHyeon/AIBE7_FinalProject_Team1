package org.example.springtestci.trade.support;

import java.sql.SQLException;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.dao.DataAccessException;

public final class PendingTransferConflictTranslator {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String CONSTRAINT = "uq_ownership_transfers_pending_product";

  private PendingTransferConflictTranslator() {}

  public static RuntimeException translate(DataAccessException exception) {
    Throwable cause = exception.getMostSpecificCause();
    if (cause instanceof SQLException sqlException
        && UNIQUE_VIOLATION.equals(sqlException.getSQLState())
        && sqlException.getMessage() != null
        && sqlException.getMessage().contains(CONSTRAINT)) {
      return new CommonException(ErrorCode.TRADE_CONFLICT);
    }
    return exception;
  }
}
