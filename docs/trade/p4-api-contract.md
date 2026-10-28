# P4 양측 거래완료 API 계약

## 적용 범위

- 기능 플래그: `app.features.trade-p4.enabled=false`가 기본값이다.
- `tradeId`는 수락된 `purchase_requests.id`다.
- 원래 채팅방 판매자와 선택 구매자만 접근하며 비당사자에게는 `404`를 반환한다.
- 진행 중 거래는 `ACCEPTED/RESERVED`, 판매글 `RESERVED`, 현재 소유자=원래 판매자여야 한다.
- 완료 거래는 `ACCEPTED/COMPLETED`, 판매글 `SOLD`이며 원래 당사자의 재요청에는 저장된 최소 결과만 반환한다.

## 완료 context

`GET /api/v1/trades/{tradeId}/completion-context?page=0&size=20`

- `page >= 0`, `1 <= size <= 100`이다.
- 제품→판매글과 판매글의 모든 구매 요청을 잠근 같은 트랜잭션에서 `products.facts_version`과 공개 판매글 projection을 조합한다.
- 응답에는 거래·판매글·제품 ID, 상태, 현재 facts 버전, 양측 저장 확인 버전, 현재 버전 확인 여부, 미확인 변경 수, 완료 가능 여부와 공개 판매글 상세가 포함된다.
- 시리얼, 증빙 원문, 제품 관리용 응답은 포함하지 않는다.
- history가 `UNAVAILABLE`이거나 `sample=true`이면 `SALE_HISTORY_001`로 거부한다. 미확인 변경은 조회를 막지 않지만 `completionAllowed=false`로 표시한다.
- 판매자 화면은 완료 확인 시 소유권 이전이 진행된다는 안내를 표시해야 한다. 한쪽만 현재 버전을 확인한 경우 상대 확인 대기 상태다.

## 완료 확인

`POST /api/v1/trades/{tradeId}/complete`

```json
{
  "confirmedFactsVersion": 7
}
```

- `confirmedFactsVersion`은 필수 양의 `Long`이다. 사용자·역할 ID는 받지 않는다.
- POST는 GET 수행 여부를 신뢰하지 않고 현재 facts 버전, READY·비샘플 공개 이력, 미확인 변경 0건을 다시 검사한다.
- 인증 사용자의 확인만 저장한다. 상대 확인이 없거나 현재 버전과 다르면 `200`, `RESERVED`, `completed=false`다.
- 양측 확인이 현재 버전과 같으면 한 트랜잭션에서 요청 거래를 `COMPLETED`, 판매글을 `SOLD`, 다른 `PENDING` 요청을 `REJECTED`로 바꾸고 정식 완료 포트와 감사를 호출한다.
- stale 버전, 미확인 변경, 취소·거절·수락 전 상태와 관계 불일치는 `TRADE_001`이다.

## 외부 완료 책임

`InternalTradeCompletionPort.complete(Command)`는 동일 DB 트랜잭션에서 제품 소유자 이전, ownership 이전 상태 확정, 시스템 TRANSFER 이력, 누적 이력 PRIVATE 및 관련 승계 정책을 처리해야 한다. 운영 구현이나 no-op 대체 Bean은 이 저장소에 제공하지 않는다. P4를 활성화했는데 정식 Bean이 없으면 애플리케이션 시작이 실패한다.

`Command`는 `productId`, `purchaseRequestId`, `listingId`, `fromUserId`, `toUserId`, `actorId`, `confirmedFactsVersion`, `completedAt`을 전달한다. 외부 구현 실패, 거래 저장 실패 또는 감사 실패는 예외를 전파해 전체 트랜잭션을 롤백해야 한다.
