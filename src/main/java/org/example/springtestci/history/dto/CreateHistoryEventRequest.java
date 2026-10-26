package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.example.springtestci.history.entity.HistoryEventType;

public record CreateHistoryEventRequest(
    @NotNull HistoryEventType eventType,
    @NotNull LocalDate occurredOn,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String content,
    @Size(max = 200) String serviceProvider,
    @PositiveOrZero Long cost,
    @Size(max = 200) String replacedPart,
    Map<String, Object> specsAfter,
    @Size(max = 20) List<@NotNull Long> documentIds) {}
