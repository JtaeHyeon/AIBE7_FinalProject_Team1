package org.example.springtestci.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 사용자가 실제로 확인한 제품 사실 버전만 전달하는 P4 완료 확인 요청이다. */
public record TradeCompletionRequest(@NotNull @Positive Long confirmedFactsVersion) {}
