package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidHistoryEventRequest(@NotBlank @Size(max = 500) String reason) {}
