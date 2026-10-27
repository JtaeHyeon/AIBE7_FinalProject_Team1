package org.example.springtestci.ai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.error.RateLimitedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gemini GenerateContent REST 연결. 자동 재시도·도구·문서·검색·채팅은 사용하지 않는다. */
@Component
@EnableConfigurationProperties(AiListingProperties.class)
public class GeminiJsonClient {
  private static final URI API =
      URI.create("https://generativelanguage.googleapis.com/v1beta/models/");
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private final AiListingProperties properties;
  private final HttpClient http;
  private final URI api;

  @Autowired
  public GeminiJsonClient(AiListingProperties properties) {
    this(
        properties,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        API);
  }

  // 테스트는 로컬 HTTP 서버만 주입한다. 운영 주소는 설정·사용자 입력으로 바꾸지 않는다.
  GeminiJsonClient(AiListingProperties properties, HttpClient http, URI api) {
    this.properties = properties;
    this.http = http;
    this.api = api;
  }

  /** 기능 활성화와 키의 기본 형식만 검사한다. 실제 키 사용 가능 여부는 외부 API 응답으로 확인된다. */
  public boolean configured() {
    String key = properties.apiKey();
    return properties.enabled()
        && key != null
        && !key.isBlank()
        && !key.matches("(?i)^(your|replace|change|sample|test|<).*")
        && !key.contains("\r")
        && !key.contains("\n");
  }

  /** 작성 지침·출력 JSON 형식·제품 자료를 Gemini에 전송한다. 제한 시간과 응답 크기도 이곳에서 관리한다. */
  public JsonNode generate(String instructions, Map<String, Object> schema, String sourceJson) {
    if (!configured()) throw new CommonException(ErrorCode.AI_UNAVAILABLE);
    // 서버 작성 지침과 공개 제품 자료를 서로 다른 메시지로 구분해 전달한다.
    var body =
        Map.of(
            "systemInstruction",
            Map.of("parts", List.of(Map.of("text", instructions))),
            "contents",
            List.of(Map.of("role", "user", "parts", List.of(Map.of("text", sourceJson)))),
            "generationConfig",
            Map.of(
                "candidateCount",
                1,
                "maxOutputTokens",
                2048,
                "responseFormat",
                Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema))));
    var request =
        HttpRequest.newBuilder(api.resolve("./" + properties.model() + ":generateContent"))
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .header("x-goog-api-key", properties.apiKey())
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
    // Future의 전체 기한은 헤더 도착 뒤 본문이 멈추는 경우까지 포함한다.
    var pending = http.sendAsync(request, ignored -> new LimitedBody());
    try {
      var response = pending.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
      if (response.statusCode() == 429)
        throw new RateLimitedException(ErrorCode.AI_RATE_LIMITED, Duration.ofSeconds(60));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new CommonException(ErrorCode.AI_UNAVAILABLE);
      return parse(response.body());
    } catch (CommonException failure) {
      throw failure;
    } catch (TimeoutException failure) {
      throw new CommonException(ErrorCode.AI_TIMEOUT);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new CommonException(ErrorCode.AI_UNAVAILABLE);
    } catch (java.util.concurrent.ExecutionException failure) {
      if (failure.getCause() instanceof java.net.http.HttpTimeoutException)
        throw new CommonException(ErrorCode.AI_TIMEOUT);
      throw new CommonException(ErrorCode.AI_UNAVAILABLE);
    } finally {
      if (!pending.isDone()) pending.cancel(true);
    }
  }

  /** 정상 종료된 텍스트 응답에서 결과 JSON을 꺼낸다. 차단·미완료·도구 호출 응답은 사용하지 않는다. */
  private JsonNode parse(byte[] body) {
    try {
      JsonNode envelope = JSON.readTree(body);
      var candidates = envelope.path("candidates");
      if (!candidates.isArray()
          || candidates.size() != 1
          || envelope.path("promptFeedback").has("blockReason")) invalid();
      var candidate = candidates.get(0);
      if (!"STOP".equals(candidate.path("finishReason").asString())) invalid();
      var parts = candidate.path("content").path("parts");
      if (!parts.isArray() || parts.isEmpty()) invalid();
      var text = new StringBuilder();
      for (var part : parts) {
        if (part.path("thought").asBoolean(false)) continue;
        if (!part.path("text").isString() || part.has("functionCall") || part.has("inlineData"))
          invalid();
        text.append(part.get("text").asString());
      }
      var result = JSON.readTree(text.toString());
      if (!result.isObject()) invalid();
      return result;
    } catch (CommonException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      // 공급자 응답·키·입력 자료를 로그나 오류 본문에 포함하지 않는다.
      throw new CommonException(ErrorCode.AI_INVALID_RESULT);
    }
  }

  private void invalid() {
    throw new CommonException(ErrorCode.AI_INVALID_RESULT);
  }

  /** 공급자 오류 본문도 최대 64KB만 수신한다. 스트림이 길어지면 즉시 취소한다. */
  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
      for (var item : items) {
        if (bytes.size() + item.remaining() > 65_536) {
          subscription.cancel();
          result.completeExceptionally(new IOException("AI response limit exceeded"));
          return;
        }
        byte[] chunk = new byte[item.remaining()];
        item.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable failure) {
      result.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
