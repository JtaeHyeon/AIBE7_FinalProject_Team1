package org.example.springtestci.document.dto;

import java.time.Instant;

public record FileUrlResponse(String url, Instant expiresAt) {}
