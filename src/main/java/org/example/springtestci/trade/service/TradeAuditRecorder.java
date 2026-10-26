package org.example.springtestci.trade.service;

import java.time.Instant;
import org.example.springtestci.trade.entity.TradeCancellationReason;

/** 예약 취소의 수행자·대상·사유·시각을 기존 감사 저장소에 남기는 포트다. */
public interface TradeAuditRecorder {

  void reservationCancelled(
      long productId,
      long actorId,
      long purchaseRequestId,
      TradeCancellationReason reason,
      Instant cancelledAt);
}
