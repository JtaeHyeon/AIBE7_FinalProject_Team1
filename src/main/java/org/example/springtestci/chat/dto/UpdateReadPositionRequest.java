package org.example.springtestci.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateReadPositionRequest(@NotNull @Positive Long lastReadMessageId) {}
