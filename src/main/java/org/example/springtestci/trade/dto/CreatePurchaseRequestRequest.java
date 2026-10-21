package org.example.springtestci.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePurchaseRequestRequest(@NotNull @Positive Long roomId) {}
