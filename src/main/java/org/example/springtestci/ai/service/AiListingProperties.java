package org.example.springtestci.ai.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 제목·본문 제안과 상태 요약이 공유하는 모델·제한 시간·요청 횟수 설정이다. API 키는 서버에서만 사용한다. */
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
    // 설정 객체를 출력하더라도 실제 API 키가 로그에 남지 않도록 가린다.
    return "AiListingProperties[enabled=" + enabled + ", model=" + model + ", apiKey=REDACTED]";
  }
}
