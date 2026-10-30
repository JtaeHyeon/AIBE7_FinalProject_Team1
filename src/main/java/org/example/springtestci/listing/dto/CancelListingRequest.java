package org.example.springtestci.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.springtestci.trade.entity.TradeCancellationReason;

public record CancelListingRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(max = 500) String reason,
    TradeCancellationReason tradeReason) {
  public CancelListingRequest(Long version, String reason) {
    this(version, reason, null);
  }

  public CancelListingRequest {
    reason = reason == null ? null : reason.strip();
  }
}
