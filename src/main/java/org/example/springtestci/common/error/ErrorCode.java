package org.example.springtestci.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "접근 권한이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "요청한 리소스를 찾을 수 없습니다."),
  DRAFT_CONFLICT(
      HttpStatus.CONFLICT, "SALE_DRAFT_001", "다른 저장본이 있거나 저장 후 내용이 변경되었습니다. 최신 저장본을 확인해 주세요."),
  DRAFT_PRODUCT_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "SALE_DRAFT_002", "제품 정보를 아직 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."),
  INVALID_EXTRACTION_STATE(HttpStatus.CONFLICT, "DOC_001", "현재 상태에서는 처리할 수 없는 추출 요청입니다."),
  INVALID_DOCUMENT_FILE(
      HttpStatus.BAD_REQUEST, "DOC_002", "JPG, PNG, PDF 형식의 올바른 파일만 업로드할 수 있습니다."),
  DOCUMENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "DOC_003", "파일은 최대 10MB까지 업로드할 수 있습니다."),
  MASKING_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "DOC_004", "개인정보 마스킹을 확인한 사본만 저장할 수 있습니다."),
  DOCUMENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "DOC_005", "제품당 업로드할 수 있는 문서 수를 초과했습니다."),
  DOCUMENT_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "DOC_006", "이력에 연결되었거나 이전받은 증빙은 삭제할 수 없습니다."),
  DOCUMENT_REDACTION_REQUIRED(
      HttpStatus.CONFLICT, "DOC_007", "개인정보 재마스킹이 필요한 증빙은 교체하기 전까지 열람할 수 없습니다."),
  DOCUMENT_STORAGE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "DOC_008", "파일 저장소를 아직 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
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
