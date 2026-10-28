package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectEventRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotBlank String title,
    String content,
    String serviceProvider,
    String replacedPart) {}
