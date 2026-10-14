package org.example.springtestci.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "접근 권한이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "요청한 리소스를 찾을 수 없습니다."),
  INVALID_SERIAL_NUMBER(HttpStatus.BAD_REQUEST, "PRODUCT_001", "시리얼 번호 형식이 올바르지 않습니다."),
  PRODUCT_ALREADY_REGISTERED(
      HttpStatus.CONFLICT, "PRODUCT_002", "이미 등록된 제품입니다. 현재 소유자에게 소유권 이전을 요청하세요."),
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_003", "제품을 찾을 수 없습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
