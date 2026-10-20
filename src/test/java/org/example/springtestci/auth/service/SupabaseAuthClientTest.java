package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.example.springtestci.common.config.SupabaseProperties;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAuthClientTest {

  private static final String URL = "https://proj.supabase.co";

  private MockRestServiceServer server;
  private SupabaseAuthClient client;

  /** 테스트마다 가짜 서버에 연결한 클라이언트를 만든다. */
  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(URL + "/auth/v1");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new SupabaseAuthClient(builder.build(), new SupabaseProperties(URL, "secret"));
  }

  /** 확인 메일이 나가면 사용자 id를 돌려준다. */
  @Test
  void signUpReturnsTheUserIdWhenAConfirmationMailWasSent() {
    server
        .expect(requestTo(URL + "/auth/v1/signup"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"id\":\"sb-1\",\"email\":\"user@example.com\",\"email_confirmed_at\":null}",
                MediaType.APPLICATION_JSON));

    assertEquals("sb-1", client.signUp("user@example.com"));
    server.verify();
  }

  /** 메일 없이 바로 인증되는 설정이면 거절하고 임시 계정을 지운다. */
  @Test
  void signUpRefusesWhenSupabaseConfirmsTheEmailWithoutAMail() {
    server
        .expect(requestTo(URL + "/auth/v1/signup"))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"t\",\"user\":{\"id\":\"sb-1\",\"email_confirmed_at\":\"2026-10-19T00:00:00Z\"}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withNoContent());

    CommonException exception =
        assertThrows(CommonException.class, () -> client.signUp("user@example.com"));

    assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.errorCode());
    server.verify();
  }

  /** 기존 계정으로 확인 메일을 다시 보내 달라고 요청한다. */
  @Test
  void resendAsksSupabaseToMailTheExistingAccountAgain() {
    server
        .expect(requestTo(URL + "/auth/v1/resend"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"type\":\"signup\",\"email\":\"user@example.com\"}"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    client.resend("user@example.com");
    server.verify();
  }

  /** 재발송 실패도 가입과 같은 방식으로 오류를 구분한다. */
  @Test
  void resendMapsFailuresTheSameWayAsSignUp() {
    server
        .expect(requestTo(URL + "/auth/v1/resend"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(requestTo(URL + "/auth/v1/resend"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertEquals(
        ErrorCode.TOO_MANY_REQUESTS,
        assertThrows(CommonException.class, () -> client.resend("user@example.com")).errorCode());
    assertEquals(
        ErrorCode.EMAIL_SEND_FAILED,
        assertThrows(CommonException.class, () -> client.resend("user@example.com")).errorCode());
  }

  /** 요청 한도 초과(429)를 횟수 제한 오류로 바꾼다. */
  @Test
  void signUpMapsARateLimitToTooManyRequests() {
    server
        .expect(requestTo(URL + "/auth/v1/signup"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    CommonException exception =
        assertThrows(CommonException.class, () -> client.signUp("user@example.com"));

    assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
  }

  /** 설정이 없으면 메일 발송 실패로 분명히 실패한다. */
  @Test
  void signUpFailsClearlyWhenSupabaseIsNotConfigured() {
    SupabaseAuthClient unconfigured =
        new SupabaseAuthClient(RestClient.create(), new SupabaseProperties("", ""));

    CommonException exception =
        assertThrows(CommonException.class, () -> unconfigured.signUp("user@example.com"));

    assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.errorCode());
  }

  /** 인증 시각이 있으면 확인됨, 없으면 미확인으로 본다. */
  @Test
  void confirmationStatusReflectsEmailConfirmedAt() {
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andRespond(
            withSuccess(
                "{\"id\":\"sb-1\",\"email_confirmed_at\":\"2026-10-19T00:00:00Z\"}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-2"))
        .andRespond(
            withSuccess(
                "{\"id\":\"sb-2\",\"email_confirmed_at\":null}", MediaType.APPLICATION_JSON));

    assertEquals(VerificationStatus.CONFIRMED, client.confirmationStatus("sb-1"));
    assertEquals(VerificationStatus.UNCONFIRMED, client.confirmationStatus("sb-2"));
  }

  /** 조회에 실패하면 알 수 없음으로 본다. */
  @Test
  void confirmationStatusIsUnknownWhenSupabaseCannotBeQueried() {
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertEquals(VerificationStatus.UNKNOWN, client.confirmationStatus("sb-1"));
  }

  /** 임시 계정 삭제는 실패해도 예외를 던지지 않는다. */
  @Test
  void deleteUserNeverThrows() {
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    client.deleteUser("sb-1");
    server.verify();
  }
}
