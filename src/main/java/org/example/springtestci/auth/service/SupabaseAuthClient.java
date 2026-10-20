package org.example.springtestci.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.example.springtestci.auth.dto.SupabaseUserResponse;
import org.example.springtestci.common.config.SupabaseProperties;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Supabase Auth의 가입 확인 메일만 빌려 쓴다. 이 서비스의 계정은 우리 users 테이블이 기준이며, Supabase 계정은 이메일 확인용 임시 계정이라 가입이
 * 끝나면 지운다. secret 키는 서버에서만 쓰고 로그에 남기지 않는다.
 */
@Component
public class SupabaseAuthClient {

  private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);
  private static final int PASSWORD_BYTES = 24;

  private final RestClient supabaseAuthRestClient;
  private final boolean configured;
  private final SecureRandom secureRandom = new SecureRandom();

  /** Supabase 호출용 클라이언트와 설정 여부를 주입받는다. */
  public SupabaseAuthClient(RestClient supabaseAuthRestClient, SupabaseProperties properties) {
    this.supabaseAuthRestClient = supabaseAuthRestClient;
    this.configured = properties.configured();
  }

  /** 임시 계정을 만들어 확인 메일을 보내게 하고, 그 계정의 id를 돌려준다. */
  public String signUp(String email) {
    if (!configured) {
      log.error(
          "SUPABASE_URL(https://xxxx.supabase.co 형태)과 SUPABASE_SECRET 설정이 없거나 올바르지 않아 인증 메일을 보낼 수 없습니다.");
      throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
    }
    byte[] bytes = new byte[PASSWORD_BYTES];
    secureRandom.nextBytes(bytes);
    String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try {
      SupabaseUserResponse response =
          supabaseAuthRestClient
              .post()
              .uri("/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("email", email, "password", password))
              .retrieve()
              .body(SupabaseUserResponse.class);
      String userId = response == null ? null : response.userId();
      // 확인 메일 없이 바로 가입되는 설정이면 소유 확인이 되지 않으므로 거절한다.
      if (userId == null || response.confirmedWithoutMail()) {
        log.error("Supabase에서 'Confirm email'이 꺼져 있어 이메일 인증을 쓸 수 없습니다.");
        deleteUser(userId);
        throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
      }
      return userId;
    } catch (RestClientException exception) {
      throw sendFailure(exception);
    }
  }

  /**
   * 이미 만든 임시 계정으로 확인 메일을 다시 보낸다. 가입 API를 다시 부르면 이메일 열거 방지 설정 때문에 다른 id가 돌아와 기존 임시 계정을 잃을 수 있어 쓰지
   * 않는다.
   */
  public void resend(String email) {
    if (!configured) {
      log.error("SUPABASE_URL과 SUPABASE_SECRET 설정이 없거나 올바르지 않아 인증 메일을 보낼 수 없습니다.");
      throw new CommonException(ErrorCode.EMAIL_SEND_FAILED);
    }
    try {
      supabaseAuthRestClient
          .post()
          .uri("/resend")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("type", "signup", "email", email))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw sendFailure(exception);
    }
  }

  /** 메일 요청 실패를 서비스 오류로 바꾼다. 요청 한도 초과(429)는 별도로 구분한다. */
  private static CommonException sendFailure(RestClientException exception) {
    if (exception instanceof RestClientResponseException responseException) {
      log.warn("Supabase 메일 요청 실패: status={}", responseException.getStatusCode().value());
      return new CommonException(
          responseException.getStatusCode().value() == 429
              ? ErrorCode.TOO_MANY_REQUESTS
              : ErrorCode.EMAIL_SEND_FAILED);
    }
    log.warn("Supabase에 연결하지 못했습니다: {}", exception.getClass().getSimpleName());
    return new CommonException(ErrorCode.EMAIL_SEND_FAILED);
  }

  /** 메일의 링크를 눌러 이메일이 확인됐는지 조회한다. 조회에 실패하면 확인 여부를 알 수 없으므로 UNKNOWN을 돌려준다. */
  public VerificationStatus confirmationStatus(String userId) {
    if (!configured) {
      return VerificationStatus.UNKNOWN;
    }
    try {
      SupabaseUserResponse response =
          supabaseAuthRestClient
              .get()
              .uri("/admin/users/{id}", userId)
              .retrieve()
              .body(SupabaseUserResponse.class);
      if (response == null) {
        return VerificationStatus.UNKNOWN;
      }
      return response.emailConfirmedAt() != null
          ? VerificationStatus.CONFIRMED
          : VerificationStatus.UNCONFIRMED;
    } catch (RestClientException exception) {
      log.warn("Supabase 확인 조회 실패: {}", exception.getClass().getSimpleName());
      return VerificationStatus.UNKNOWN;
    }
  }

  /** 임시 계정을 지운다. 실패해도 가입 흐름은 막지 않는다. */
  public void deleteUser(String userId) {
    if (!configured || userId == null) {
      return;
    }
    try {
      supabaseAuthRestClient
          .delete()
          .uri("/admin/users/{id}", userId)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      log.warn("Supabase 임시 계정 삭제 실패: {}", exception.getClass().getSimpleName());
    }
  }
}
