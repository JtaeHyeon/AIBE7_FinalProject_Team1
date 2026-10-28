package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;

public record CreateEventRequest(
    @NotNull @Positive Long productId,
    @NotNull EventType eventType,
    @NotNull @PastOrPresent LocalDate occurredOn,
    @NotBlank String title,
    String content,
    String serviceProvider,
    String replacedPart,
    EventVisibility visibility) {}
