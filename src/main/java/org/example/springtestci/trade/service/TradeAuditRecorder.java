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

  /** 양측 최신 확인으로 확정된 거래 완료 사실을 기존 감사 저장소에 기록한다. */
  void tradeCompleted(
      long productId,
      long actorId,
      long purchaseRequestId,
      long confirmedFactsVersion,
      Instant completedAt);
}
