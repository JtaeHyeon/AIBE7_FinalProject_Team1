package org.example.springtestci.auth.service;

/** 이메일 확인 조회 결과. UNKNOWN은 조회에 실패했거나 근거가 사라져 판단할 수 없는 경우라, 이 상태의 계정은 지우지 않는다. */
public enum VerificationStatus {
  CONFIRMED,
  UNCONFIRMED,
  UNKNOWN,
  /** 대기 기록이 만료돼 더 이상 확인할 수 없다. */
  EXPIRED
}
