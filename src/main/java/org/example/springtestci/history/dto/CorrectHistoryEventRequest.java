package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record CorrectHistoryEventRequest(
    @NotNull LocalDate occurredOn,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String content,
    @Size(max = 200) String serviceProvider,
    @PositiveOrZero Long cost,
    @Size(max = 200) String replacedPart,
    Map<String, Object> specsAfter,
    @NotBlank @Size(max = 500) String reason) {}
