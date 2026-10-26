package org.example.springtestci.history.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.security.CurrentUserIdResolver;
import org.example.springtestci.history.dto.CorrectHistoryEventRequest;
import org.example.springtestci.history.dto.CreateHistoryEventRequest;
import org.example.springtestci.history.dto.HistoryEventResponse;
import org.example.springtestci.history.dto.VoidHistoryEventRequest;
import org.example.springtestci.history.entity.HistoryEventType;
import org.example.springtestci.history.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

  private static final long OWNER = 42L;
  private static final long PRODUCT = 10L;

  @Mock private HistoryService historyService;

  private HistoryController historyController;

  @BeforeEach
  void setUp() {
    historyController = new HistoryController(historyService, new CurrentUserIdResolver());
  }

  @Test
  void createsHistoryForTheAuthenticatedOwner() {
    CreateHistoryEventRequest request =
        new CreateHistoryEventRequest(
            HistoryEventType.REPAIR,
            LocalDate.of(2026, 10, 20),
            "키보드 수리",
            null,
            null,
            null,
            null,
            null,
            List.of());
    HistoryEventResponse event = org.mockito.Mockito.mock(HistoryEventResponse.class);
    when(historyService.create(OWNER, PRODUCT, request)).thenReturn(event);

    var response = historyController.create(jwt("42"), PRODUCT, request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(event, response.getBody().data());
    verify(historyService).create(OWNER, PRODUCT, request);
  }

  @Test
  void returnsTheOwnedProductsTimeline() {
    List<HistoryEventResponse> timeline = List.of();
    when(historyService.timeline(OWNER, PRODUCT)).thenReturn(timeline);

    var response = historyController.timeline(jwt("42"), PRODUCT);

    assertEquals(timeline, response.data());
    verify(historyService).timeline(OWNER, PRODUCT);
  }

  @Test
  void correctsHistoryUsingOnlyTheAuthenticatedUserId() {
    CorrectHistoryEventRequest request =
        new CorrectHistoryEventRequest(
            LocalDate.of(2026, 10, 19),
            "키보드 상판 교체",
            null,
            "공식 서비스센터",
            120000L,
            "키보드 상판",
            null,
            "수리 명세서 확인");
    HistoryEventResponse event = org.mockito.Mockito.mock(HistoryEventResponse.class);
    when(historyService.correct(OWNER, PRODUCT, 20L, request)).thenReturn(event);

    var response = historyController.correct(jwt("42"), PRODUCT, 20L, request);

    assertEquals(event, response.data());
    verify(historyService).correct(OWNER, PRODUCT, 20L, request);
  }

  @Test
  void voidsHistoryUsingOnlyTheAuthenticatedUserId() {
    VoidHistoryEventRequest request = new VoidHistoryEventRequest("중복 등록");
    HistoryEventResponse event = org.mockito.Mockito.mock(HistoryEventResponse.class);
    when(historyService.voidEvent(OWNER, PRODUCT, 20L, request)).thenReturn(event);

    var response = historyController.voidEvent(jwt("42"), PRODUCT, 20L, request);

    assertEquals(event, response.data());
    verify(historyService).voidEvent(OWNER, PRODUCT, 20L, request);
  }

  @Test
  void rejectsAnInvalidJwtSubjectBeforeCallingTheService() {
    CommonException exception =
        assertThrows(
            CommonException.class, () -> historyController.timeline(jwt("not-a-user"), PRODUCT));

    assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
  }

  private Jwt jwt(String subject) {
    return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).build();
  }
}
