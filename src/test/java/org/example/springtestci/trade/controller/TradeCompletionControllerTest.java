package org.example.springtestci.trade.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.error.GlobalExceptionHandler;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.TradeCompletionResponse;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.TradeCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TradeCompletionControllerTest {

  @Mock private TradeCompletionService completionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TradeCompletionController(completionService, new CurrentUserIdResolver()))
            .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
            .build();
  }

  @Test
  void confirmsAuthenticatedUsersVersionAndReturnsCompletionState() throws Exception {
    when(completionService.complete(41L, 91L, 7L))
        .thenReturn(
            new TradeCompletionResponse(
                41L,
                PurchaseRequestStatus.ACCEPTED,
                TransactionStatus.COMPLETED,
                PublishedListingStatus.SOLD,
                7L,
                7L,
                true));

    mockMvc
        .perform(
            post("/api/v1/trades/41/complete")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedFactsVersion\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.listingStatus").value("SOLD"))
        .andExpect(jsonPath("$.data.completed").value(true));
  }

  @Test
  void rejectsMissingZeroAndNegativeVersion() throws Exception {
    for (String body :
        new String[] {"{}", "{\"confirmedFactsVersion\":0}", "{\"confirmedFactsVersion\":-1}"}) {
      mockMvc
          .perform(
              post("/api/v1/trades/41/complete")
                  .principal(authentication(91L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
  }

  @Test
  void preservesCommonConflictContract() throws Exception {
    when(completionService.complete(41L, 91L, 7L))
        .thenThrow(new CommonException(ErrorCode.TRADE_CONFLICT));

    mockMvc
        .perform(
            post("/api/v1/trades/41/complete")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedFactsVersion\":7}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRADE_001"));
  }

  @Test
  void rejectsRequestWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/trades/41/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedFactsVersion\":7}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"));
  }

  private TestingAuthenticationToken authentication(long userId) {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").subject(String.valueOf(userId)).build();
    return new TestingAuthenticationToken(jwt, null, "ROLE_USER");
  }
}
