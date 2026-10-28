package org.example.springtestci.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.common.config.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  /** 오류 응답의 시각을 기록할 시계를 주입받는다. */
  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** 요청 본문의 검증 실패를 항목별 메시지와 함께 응답한다. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return response(ErrorCode.INVALID_INPUT, request, fieldErrors);
  }

  /** 파라미터 검증 실패를 항목별 메시지와 함께 응답한다. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        exception.getConstraintViolations().stream()
            .map(
                violation ->
                    new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();
    return response(ErrorCode.INVALID_INPUT, request, fieldErrors);
  }

  /** 횟수 제한 오류를 429와 Retry-After 헤더로 응답한다. */
  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ErrorResponse> handleRateLimited(
      RateLimitedException exception, HttpServletRequest request) {
    long seconds = Math.max(1, exception.retryAfter().toSeconds());
    long minutes = (seconds + 59) / 60;
    ErrorCode errorCode = exception.errorCode();
    ResponseEntity<ErrorResponse> base =
        response(
            errorCode.status(),
            errorCode.code(),
            errorCode == ErrorCode.LOGIN_RATE_LIMITED
                ? "로그인 시도가 너무 많아요. %d분 뒤에 다시 시도해 주세요.".formatted(minutes)
                : errorCode.message() + " (%d초 후 재시도)".formatted(seconds),
            request,
            List.of());
    return ResponseEntity.status(base.getStatusCode())
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
        .body(base.getBody());
  }

  /** 서비스에서 던진 업무 오류를 오류 코드에 맞는 응답으로 바꾼다. */
  @ExceptionHandler(CommonException.class)
  ResponseEntity<ErrorResponse> handleCommon(
      CommonException exception, HttpServletRequest request) {
    return response(exception.errorCode(), request, List.of());
  }

  /** 예상하지 못한 오류를 내부 정보 없이 공통 형식으로 응답한다. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
    if (exception instanceof HttpMessageNotReadableException
        || exception instanceof TypeMismatchException) {
      return response(ErrorCode.INVALID_INPUT, request, List.of());
    }

    if (exception instanceof org.springframework.web.ErrorResponse webError) {
      HttpStatusCode status = webError.getStatusCode();
      return response(status, "WEB_" + status.value(), "요청을 처리할 수 없습니다.", request, List.of());
    }

    log.error(
        "Unhandled exception requestId={}, type={}", requestId(), exception.getClass().getName());
    return response(ErrorCode.INTERNAL_ERROR, request, List.of());
  }

  /** 검증 실패 항목을 응답 형식으로 바꾼다. */
  private ErrorResponse.FieldError toFieldError(FieldError error) {
    return new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage());
  }

  /** 오류 코드로 공통 오류 응답을 만든다. */
  private ResponseEntity<ErrorResponse> response(
      ErrorCode errorCode, HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
    return response(
        errorCode.status(), errorCode.code(), errorCode.message(), request, fieldErrors);
  }

  /** 상태, 코드, 메시지를 직접 받아 공통 오류 응답을 만든다. */
  private ResponseEntity<ErrorResponse> response(
      HttpStatusCode status,
      String code,
      String message,
      HttpServletRequest request,
      List<ErrorResponse.FieldError> fieldErrors) {
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            code,
            message,
            request.getRequestURI(),
            requestId(),
            fieldErrors);
    return ResponseEntity.status(status).body(body);
  }

  /** 현재 요청의 추적 id를 돌려준다. */
  private String requestId() {
    return MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
  }
}
