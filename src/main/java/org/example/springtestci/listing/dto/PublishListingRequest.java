package org.example.springtestci.listing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PublishListingRequest(
    @NotNull @Positive Long draftId, @NotNull @PositiveOrZero Long version) {}
