package org.example.springtestci.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Supabase Auth의 가입 확인 메일만 빌려 쓴다. 이 서비스의 계정은 우리 users 테이블이 기준이며, Supabase 계정은 이메일 확인용 임시 계정이라 가입이
 * 끝나면 지운다. service key는 서버에서만 쓰고 로그에 남기지 않는다.
 */
@Component
public class SupabaseAuthClient {

  private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
      new ParameterizedTypeReference<>() {};
  private static final int PASSWORD_BYTES = 24;

  private final RestClient restClient;
  private final boolean configured;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  public SupabaseAuthClient(
      @Value("${app.supabase.url:}") String url,
      @Value("${app.supabase.service-key:}") String serviceKey) {
    this(RestClient.builder(), url, serviceKey);
  }

  SupabaseAuthClient(RestClient.Builder builder, String url, String serviceKey) {
    this.configured =
        StringUtils.hasText(url)
            && StringUtils.hasText(serviceKey)
            && (url.strip().startsWith("https://") || url.strip().startsWith("http://"));
    RestClient.Builder configuredBuilder =
        builder.baseUrl(configured ? url.strip().replaceAll("/+$", "") + "/auth/v1" : "");
    if (configured) {
      configuredBuilder.defaultHeader("apikey", serviceKey);
      // 새 형식 키(sb_secret_...)는 JWT가 아니라 Authorization 헤더에 보내지 않는다.
      if (serviceKey.startsWith("eyJ")) {
        configuredBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey);
      }
    }
    this.restClient = configuredBuilder.build();
  }

  /** 임시 계정을 만들어 확인 메일을 보내게 하고, 그 계정의 id를 돌려준다. */
  public String signUp(String email) {
    requireConfigured();
    byte[] bytes = new byte[PASSWORD_BYTES];
    secureRandom.nextBytes(bytes);
    String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try {
      Map<String, Object> body =
          restClient
              .post()
              .uri("/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("email", email, "password", password))
              .retrieve()
              .body(JSON_MAP);
      String id = userId(body);
      // 확인 메일 없이 바로 가입되는 설정이면 소유 확인이 되지 않으므로 거절한다.
      if (id == null
          || body.containsKey("access_token")
          || body.get("email_confirmed_at") != null) {
        log.error("Supabase에서 'Confirm email'이 꺼져 있어 이메일 인증을 쓸 수 없습니다.");
        deleteUser(id);
        throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
      }
      return id;
    } catch (RestClientResponseException exception) {
      log.warn("Supabase 가입 요청 실패: status={}", exception.getStatusCode().value());
      throw new CommonException(
          exception.getStatusCode().value() == 429
              ? ErrorCode.TOO_MANY_REQUESTS
              : ErrorCode.EMAIL_SEND_FAILED);
    } catch (org.springframework.web.client.RestClientException exception) {
      log.warn("Supabase에 연결하지 못했습니다: {}", exception.getClass().getSimpleName());
      throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
    }
  }

  /** 메일의 링크를 눌러 이메일이 확인됐는지 조회한다. 조회에 실패하면 아직 확인되지 않은 것으로 본다. */
  public boolean isConfirmed(String userId) {
    if (!configured) {
      return false;
    }
    try {
      Map<String, Object> body =
          restClient.get().uri("/admin/users/{id}", userId).retrieve().body(JSON_MAP);
      return body != null && body.get("email_confirmed_at") != null;
    } catch (org.springframework.web.client.RestClientException exception) {
      log.warn("Supabase 확인 조회 실패: {}", exception.getClass().getSimpleName());
      return false;
    }
  }

  /** 임시 계정을 지운다. 실패해도 가입 흐름은 막지 않는다. */
  public void deleteUser(String userId) {
    if (!configured || userId == null) {
      return;
    }
    try {
      restClient.delete().uri("/admin/users/{id}", userId).retrieve().toBodilessEntity();
    } catch (org.springframework.web.client.RestClientException exception) {
      log.warn("Supabase 임시 계정 삭제 실패: {}", exception.getClass().getSimpleName());
    }
  }

  private void requireConfigured() {
    if (!configured) {
      log.error(
          "SUPABASE_URL(https://xxxx.supabase.co 형태)과 SUPABASE_SERVICE_KEY 설정이 없거나 올바르지 않아 인증 메일을 보낼 수 없습니다.");
      throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
    }
  }

  /** 가입 응답은 사용자 객체이거나, 세션과 함께 user 안에 들어 있다. */
  @SuppressWarnings("unchecked")
  private static String userId(Map<String, Object> body) {
    if (body == null) {
      return null;
    }
    Object id = body.get("id");
    if (id == null && body.get("user") instanceof Map<?, ?> user) {
      id = ((Map<String, Object>) user).get("id");
    }
    return id == null ? null : id.toString();
  }
}
