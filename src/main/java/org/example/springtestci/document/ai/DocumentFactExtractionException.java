package org.example.springtestci.document.ai;

/** AI 추출 실패. 오류 원문이나 문서 내용을 담지 않고 분류 코드만 담는다. */
public class DocumentFactExtractionException extends Exception {

  private final String errorCode;

  public DocumentFactExtractionException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  public DocumentFactExtractionException(String errorCode, Throwable cause) {
    super(errorCode, cause);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
