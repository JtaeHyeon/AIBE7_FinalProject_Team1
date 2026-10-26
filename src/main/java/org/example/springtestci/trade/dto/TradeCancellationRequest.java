package org.example.springtestci.trade.dto;

import jakarta.validation.constraints.NotNull;
import org.example.springtestci.trade.entity.TradeCancellationReason;

public record TradeCancellationRequest(@NotNull TradeCancellationReason reason) {}
