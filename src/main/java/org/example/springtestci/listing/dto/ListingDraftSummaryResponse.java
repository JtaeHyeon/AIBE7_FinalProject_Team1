package org.example.springtestci.listing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingDraftSummaryResponse(
    long draftId,
    long productId,
    String productTitle,
    String title,
    BigDecimal price,
    Integer conditionGrade,
    long version,
    Instant updatedAt) {}
