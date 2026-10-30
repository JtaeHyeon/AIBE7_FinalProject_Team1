package org.example.springtestci.ownership.event;

/** 소유자 변경 직후 같은 트랜잭션에서 발행한다. 필수 후처리 실패도 이전 전체를 롤백해야 한다. */
public record OwnershipTransferred(long productId, long previousOwnerId, long newOwnerId) {}
