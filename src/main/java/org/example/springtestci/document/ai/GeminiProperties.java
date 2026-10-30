package org.example.springtestci.document.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.ai.gemini.*} 설정. API 키는 환경 변수로만 주입하고 코드·로그에 남기지 않는다. */
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(
    String apiKey, String model, String baseUrl, Integer timeoutSeconds) {

  public String modelOrDefault() {
    return model == null || model.isBlank() ? "gemini-3.5-flash-lite" : model;
  }

  public String baseUrlOrDefault() {
    return baseUrl == null || baseUrl.isBlank()
        ? "https://generativelanguage.googleapis.com"
        : baseUrl.replaceAll("/+$", "");
  }

  public int timeoutSecondsOrDefault() {
    return timeoutSeconds == null || timeoutSeconds < 1 ? 30 : timeoutSeconds;
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
