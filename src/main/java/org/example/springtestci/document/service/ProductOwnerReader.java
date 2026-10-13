package org.example.springtestci.document.service;

import java.util.Optional;

/** 제품의 현재 소유자를 조회한다. 제품 도메인 서비스가 생기면 그쪽 구현으로 교체한다. */
public interface ProductOwnerReader {

  Optional<Long> findOwnerId(Long productId);
}
