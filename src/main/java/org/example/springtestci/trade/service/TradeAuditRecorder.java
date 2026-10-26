package org.example.springtestci.trade.service;

import java.time.Instant;
import org.example.springtestci.trade.entity.TradeCancellationReason;

public interface TradeAuditRecorder {

  void reservationCancelled(
      long productId,
      long actorId,
      long purchaseRequestId,
      TradeCancellationReason reason,
      Instant cancelledAt);
}
