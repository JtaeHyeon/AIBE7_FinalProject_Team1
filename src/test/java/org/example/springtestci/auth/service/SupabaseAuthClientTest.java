package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(URL + "/auth/v1");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new SupabaseAuthClient(builder.build(), new SupabaseProperties(URL, "secret"));
  }

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

  @Test
  void signUpMapsARateLimitToTooManyRequests() {
    server
        .expect(requestTo(URL + "/auth/v1/signup"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    CommonException exception =
        assertThrows(CommonException.class, () -> client.signUp("user@example.com"));

    assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
  }

  @Test
  void signUpFailsClearlyWhenSupabaseIsNotConfigured() {
    SupabaseAuthClient unconfigured =
        new SupabaseAuthClient(RestClient.create(), new SupabaseProperties("", ""));

    CommonException exception =
        assertThrows(CommonException.class, () -> unconfigured.signUp("user@example.com"));

    assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.errorCode());
  }

  @Test
  void isConfirmedReflectsEmailConfirmedAt() {
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

    assertTrue(client.isConfirmed("sb-1"));
    assertFalse(client.isConfirmed("sb-2"));
  }

  @Test
  void isConfirmedIsFalseWhenSupabaseCannotBeQueried() {
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertFalse(client.isConfirmed("sb-1"));
  }

  @Test
  void deleteUserNeverThrows() {
    server
        .expect(requestTo(URL + "/auth/v1/admin/users/sb-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    client.deleteUser("sb-1");
    server.verify();
  }
}
