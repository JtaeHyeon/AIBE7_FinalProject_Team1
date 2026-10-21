package org.example.springtestci.trade.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import org.example.springtestci.common.error.GlobalExceptionHandler;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.trade.dto.PurchaseRequestResponse;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.service.PurchaseRequestService;
import org.example.springtestci.trade.service.PurchaseRequestService.CreateResult;
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
class PurchaseRequestControllerTest {

  @Mock private PurchaseRequestService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PurchaseRequestController(service, new CurrentUserIdResolver()))
            .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
            .build();
  }

  @Test
  void returnsCreatedForNewRequest() throws Exception {
    when(service.create(anyLong(), anyLong(), anyLong()))
        .thenReturn(new CreateResult(pendingResponse(), true));

    mockMvc
        .perform(
            post("/api/v1/market/listings/61/purchase-requests")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":51}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.requestId").value(41))
        .andExpect(jsonPath("$.data.requestStatus").value("PENDING"));
  }

  @Test
  void returnsOkForReusedPendingRequest() throws Exception {
    when(service.create(anyLong(), anyLong(), anyLong()))
        .thenReturn(new CreateResult(pendingResponse(), false));

    mockMvc
        .perform(
            post("/api/v1/market/listings/61/purchase-requests")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":51}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requestId").value(41));
  }

  @Test
  void rejectsMissingRoomId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/market/listings/61/purchase-requests")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));
  }

  @Test
  void rejectsRequestWithoutJwtAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/market/listings/61/purchase-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":51}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"));
  }

  private PurchaseRequestResponse pendingResponse() {
    return new PurchaseRequestResponse(
        41L, 51L, 61L, PurchaseRequestStatus.PENDING, null, PublishedListingStatus.ACTIVE);
  }

  private TestingAuthenticationToken authentication() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("91").build();
    return new TestingAuthenticationToken(jwt, null, "ROLE_USER");
  }
}
