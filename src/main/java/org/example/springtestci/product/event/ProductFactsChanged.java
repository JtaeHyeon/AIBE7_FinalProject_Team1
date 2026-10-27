package org.example.springtestci.product.event;

/** 제품·사양·이력·공개 범위 변경 측에서 같은 트랜잭션의 facts_version 증가와 함께 발행할 이벤트다. */
public record ProductFactsChanged(long productId) {}
