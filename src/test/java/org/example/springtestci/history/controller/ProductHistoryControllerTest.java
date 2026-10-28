package org.example.springtestci.history.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.common.error.GlobalExceptionHandler;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.history.dto.ProductEventResponse;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.service.ProductHistoryService;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class ProductHistoryControllerTest {

  @Mock private ProductHistoryService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ProductHistoryController(service, new CurrentUserIdResolver()))
            .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
            .build();
  }

  private TestingAuthenticationToken authentication(long userId) {
    Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claim("sub", String.valueOf(userId))
            .build();
    return new TestingAuthenticationToken(jwt, null, "ROLE_USER");
  }

  @Test
  @DisplayName("제품 이력 등록 API")
  void createEvent() throws Exception {
    ProductEventResponse response =
        new ProductEventResponse(
            1L,
            10L,
            1L,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 1),
            "액정 수리",
            "정품 교체",
            "Apple",
            "LCD",
            EventVisibility.PUBLIC,
            EventStatus.VALID,
            Instant.now(),
            Instant.now(),
            List.of());

    given(service.createEvent(eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/v1/products/10/events")
                .principal(authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "productId": 10,
                      "eventType": "REPAIR",
                      "occurredOn": "2025-01-01",
                      "title": "액정 수리",
                      "content": "정품 교체"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.eventId").value(1))
        .andExpect(jsonPath("$.data.title").value("액정 수리"));
  }

  @Test
  @DisplayName("제품 이력 목록 조회 API")
  void getEvents() throws Exception {
    ProductEventResponse response =
        new ProductEventResponse(
            1L,
            10L,
            1L,
            EventType.PURCHASE,
            LocalDate.of(2024, 1, 1),
            "신품 구매",
            "공식 스토어",
            null,
            null,
            EventVisibility.PUBLIC,
            EventStatus.VALID,
            Instant.now(),
            Instant.now(),
            List.of());

    given(service.getEvents(any(), eq(10L))).willReturn(List.of(response));

    mockMvc
        .perform(get("/api/v1/products/10/events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].eventId").value(1))
        .andExpect(jsonPath("$.data[0].title").value("신품 구매"));
  }

  @Test
  @DisplayName("제품 이력 정정 API")
  void correctEvent() throws Exception {
    ProductEventResponse response =
        new ProductEventResponse(
            1L,
            10L,
            1L,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 1),
            "수정된 제목",
            "수정된 내용",
            null,
            null,
            EventVisibility.PUBLIC,
            EventStatus.VALID,
            Instant.now(),
            Instant.now(),
            List.of());

    given(service.correctEvent(eq(1L), eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/v1/events/1/correct")
                .principal(authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "오타 정정",
                      "title": "수정된 제목",
                      "content": "수정된 내용"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.title").value("수정된 제목"));
  }

  @Test
  @DisplayName("제품 이력 공개 상태 변경 API")
  void changeVisibility() throws Exception {
    ProductEventResponse response =
        new ProductEventResponse(
            1L,
            10L,
            1L,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 1),
            "액정 수리",
            null,
            null,
            null,
            EventVisibility.PUBLIC,
            EventStatus.VALID,
            Instant.now(),
            Instant.now(),
            List.of());

    given(service.changeVisibility(eq(1L), eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/v1/events/1/visibility")
                .principal(authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PUBLIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));

    mockMvc
        .perform(
            put("/api/v1/events/1/visibility")
                .principal(authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("제품 이력 무효화 API")
  void voidEvent() throws Exception {
    ProductEventResponse response =
        new ProductEventResponse(
            1L,
            10L,
            1L,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 1),
            "액정 수리",
            "정품 교체",
            null,
            null,
            EventVisibility.PUBLIC,
            EventStatus.VOID,
            Instant.now(),
            Instant.now(),
            List.of());

    given(service.voidEvent(eq(1L), eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/v1/events/1/void")
                .principal(authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"중복 등록\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("VOID"));
  }
}
