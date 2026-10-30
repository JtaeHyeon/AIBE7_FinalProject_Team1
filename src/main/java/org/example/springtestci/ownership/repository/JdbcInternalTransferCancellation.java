package org.example.springtestci.ownership.repository;

import java.sql.Timestamp;
import java.time.Clock;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.trade.service.InternalTransferCancellationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 정식 소유권 테이블의 내부 이전 취소 구현이다. 거래 서비스의 제품→판매글→구매 요청 잠금 이후 실행한다. */
@Repository
@ConditionalOnProperty(prefix = "app.features.trade-p3", name = "enabled", havingValue = "true")
public class JdbcInternalTransferCancellation implements InternalTransferCancellationPort {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public JdbcInternalTransferCancellation(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** 제품·구매 요청·양측 사용자가 일치하는 INTERNAL/PENDING 한 건만 취소하고 대기 슬롯을 해제한다. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void cancelPending(Command command) {
    if (command == null
        || command.productId() <= 0
        || command.purchaseRequestId() <= 0
        || command.fromUserId() <= 0
        || command.toUserId() <= 0
        || command.fromUserId() == command.toUserId())
      throw new CommonException(ErrorCode.TRADE_CONFLICT);
    try {
      int updated =
          jdbc.update(
              """
          UPDATE ownership_transfers SET status='CANCELLED',pending_slot=NULL,cancelled_at=?
           WHERE product_id=? AND purchase_request_id=? AND from_user_id=? AND to_user_id=?
             AND transfer_type='INTERNAL' AND status='PENDING'
          """,
              Timestamp.from(clock.instant()),
              command.productId(),
              command.purchaseRequestId(),
              command.fromUserId(),
              command.toUserId());
      // 재시도는 호출 서비스가 처리한다. 누락·다른 거래·이미 확정된 이전을 성공으로 가장하지 않는다.
      if (updated != 1) throw new CommonException(ErrorCode.TRADE_CONFLICT);
    } catch (DataAccessException failure) {
      throw new CommonException(ErrorCode.TRADE_DEPENDENCY_UNAVAILABLE);
    }
  }
}
