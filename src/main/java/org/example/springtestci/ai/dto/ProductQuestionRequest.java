package org.example.springtestci.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductQuestionRequest(@NotBlank @Size(max = 1000) String question) {}
