package org.example.springtestci.common.error;

import java.time.Duration;

/** 요청 횟수 제한에 걸린 경우. 다시 시도할 수 있기까지 남은 시간을 함께 알린다. */
public class RateLimitedException extends CommonException {

  private final Duration retryAfter;

  public RateLimitedException(ErrorCode errorCode, Duration retryAfter) {
    super(errorCode);
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
