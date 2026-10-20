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

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return response(ErrorCode.INVALID_INPUT, request, fieldErrors);
  }

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

  @ExceptionHandler(CommonException.class)
  ResponseEntity<ErrorResponse> handleCommon(
      CommonException exception, HttpServletRequest request) {
    return response(exception.errorCode(), request, List.of());
  }

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

  private ErrorResponse.FieldError toFieldError(FieldError error) {
    return new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      ErrorCode errorCode, HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
    return response(
        errorCode.status(), errorCode.code(), errorCode.message(), request, fieldErrors);
  }

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

  private String requestId() {
    return MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
  }
}
