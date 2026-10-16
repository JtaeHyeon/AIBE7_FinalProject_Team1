package org.example.springtestci.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

class GlobalExceptionHandlerTest {

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler(Clock.systemUTC());

  GlobalExceptionHandlerTest() {
    when(request.getRequestURI()).thenReturn("/api/test");
  }

  @Test
  void returnsBadRequestForAnUnreadableRequestBody() {
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("malformed JSON", mock(HttpInputMessage.class));

    ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(ErrorCode.INVALID_INPUT.code(), response.getBody().code());
  }

  @Test
  void returnsBadRequestWhenAPathValueHasTheWrongType() {
    org.springframework.beans.TypeMismatchException exception =
        new org.springframework.beans.TypeMismatchException("abc", Long.class);

    ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(ErrorCode.INVALID_INPUT.code(), response.getBody().code());
  }

  @Test
  void preservesOtherBuiltInClientErrorStatuses() {
    HttpMediaTypeNotSupportedException exception =
        new HttpMediaTypeNotSupportedException(
            MediaType.APPLICATION_XML,
            java.util.List.of(MediaType.APPLICATION_JSON),
            HttpMethod.POST);

    ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertEquals("WEB_415", response.getBody().code());
  }
}
