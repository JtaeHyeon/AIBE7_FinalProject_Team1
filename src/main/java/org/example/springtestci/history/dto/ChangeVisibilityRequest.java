package org.example.springtestci.history.dto;

import jakarta.validation.constraints.NotNull;
import org.example.springtestci.history.entity.EventVisibility;

public record ChangeVisibilityRequest(@NotNull EventVisibility visibility) {}
