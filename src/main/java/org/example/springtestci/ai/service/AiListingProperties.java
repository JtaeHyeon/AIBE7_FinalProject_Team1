package org.example.springtestci.ai.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** API 키는 서버 설정에서만 읽고 DTO·로그에 포함하지 않는다. */
@Validated
@ConfigurationProperties("app.ai.listing")
public record AiListingProperties(
    boolean enabled,
    String apiKey,
    @Pattern(regexp = "[a-zA-Z0-9._-]+") String model,
    @Min(1) @Max(60) int timeoutSeconds,
    @Min(1) @Max(30) int userRequestsPerMinute,
    @Min(1) @Max(1000) int globalRequestsPerMinute) {
  @Override
  public String toString() {
    return "AiListingProperties[enabled=" + enabled + ", model=" + model + ", apiKey=REDACTED]";
  }
}
