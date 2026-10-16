package org.example.springtestci.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(@NotBlank @Size(max = 64) String password) {}
