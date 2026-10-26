package org.example.springtestci.trade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.error.GlobalExceptionHandler;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.ChangeReviewPageResponse;
import org.example.springtestci.trade.dto.ChangeReviewPageResponse.PageMetadata;
import org.example.springtestci.trade.dto.ChangeReviewResponse;
import org.example.springtestci.trade.dto.TradeCancellationResponse;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TradeCancellationReason;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.ReservationChangeService;
import org.example.springtestci.trade.service.TradeCancellationService;
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
class TradeControllerTest {

  @Mock private TradeCancellationService cancellationService;
  @Mock private ReservationChangeService changeService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TradeController(
                    cancellationService, changeService, new CurrentUserIdResolver()))
            .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
            .build();
  }

  @Test
  void cancelsReservedTradeWithAuthenticatedActor() throws Exception {
    when(cancellationService.cancel(41L, 91L, TradeCancellationReason.SELF_CIRCUMSTANCES))
        .thenReturn(cancelledResponse());

    mockMvc
        .perform(
            post("/api/v1/trades/41/cancel")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SELF_CIRCUMSTANCES\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.requestStatus").value("CANCELLED"))
        .andExpect(jsonPath("$.data.cancelledById").value(91));
  }

  @Test
  void rejectsMissingOrUnknownCancellationReason() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/trades/41/cancel")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));

    mockMvc
        .perform(
            post("/api/v1/trades/41/cancel")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));
  }

  @Test
  void listsChangeReviewsWithBoundedPageParameters() throws Exception {
    when(changeService.findAll(41L, 81L, 0, 20))
        .thenReturn(
            new ChangeReviewPageResponse(
                List.of(), new PageMetadata(0, 20, 0, 0), 0, TransactionStatus.RESERVED));

    mockMvc
        .perform(get("/api/v1/trades/41/change-reviews").principal(authentication(81L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreviewedCount").value(0))
        .andExpect(jsonPath("$.data.transactionStatus").value("RESERVED"));
  }

  @Test
  void buyerReviewsChangeAndServiceErrorsKeepHttpContract() throws Exception {
    when(changeService.review(41L, 42L, 91L, ChangeReviewDecision.KEEP, null))
        .thenReturn(
            new ChangeReviewResponse(42L, 3L, null, ChangeReviewDecision.KEEP, Instant.EPOCH));

    mockMvc
        .perform(
            post("/api/v1/trades/41/change-reviews/42")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"KEEP\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("KEEP"));

    when(changeService.review(anyLong(), anyLong(), anyLong(), any(), any()))
        .thenThrow(new CommonException(ErrorCode.ACCESS_DENIED));
    mockMvc
        .perform(
            post("/api/v1/trades/41/change-reviews/42")
                .principal(authentication(81L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"KEEP\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_002"));
  }

  @Test
  void rejectsCancelDecisionWithoutReason() throws Exception {
    when(changeService.review(41L, 42L, 91L, ChangeReviewDecision.CANCEL, null))
        .thenThrow(new CommonException(ErrorCode.INVALID_INPUT));

    mockMvc
        .perform(
            post("/api/v1/trades/41/change-reviews/42")
                .principal(authentication(91L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"CANCEL\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));
  }

  @Test
  void rejectsRequestWithoutJwtAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/trades/41/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SELF_CIRCUMSTANCES\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"));
  }

  private TradeCancellationResponse cancelledResponse() {
    return new TradeCancellationResponse(
        41L,
        51L,
        61L,
        PurchaseRequestStatus.CANCELLED,
        TransactionStatus.CANCELLED,
        PublishedListingStatus.ACTIVE,
        91L,
        TradeCancellationReason.SELF_CIRCUMSTANCES,
        Instant.EPOCH);
  }

  private TestingAuthenticationToken authentication(long userId) {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").subject(String.valueOf(userId)).build();
    return new TestingAuthenticationToken(jwt, null, "ROLE_USER");
  }
}
