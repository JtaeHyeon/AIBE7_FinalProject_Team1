# P3 예약 취소·변경 확인 계약

## 기능 경계

- 거래 식별자는 수락된 `purchase_requests.id`다. 별도 `transactions` 테이블을 만들지 않는다.
- 기존 `POST /api/v1/purchase-requests/{requestId}/cancel`은 PENDING 구매 요청 취소로 유지한다.
- P3 API는 `app.features.trade-p3.enabled=true`일 때만 등록되며 기본값은 `false`다.
- 인증 사용자는 기존 JWT `sub`에서 식별한다. 사용자 ID·취소 수행 시각·변경 사본을 요청 본문으로 받지 않는다.
- 정식 ownership/history 구현이 없으면 P3 on 구성은 실패한다. 운영 no-op이나 성공 고정 빈은 제공하지 않는다.

## 예약 취소

### `POST /api/v1/trades/{tradeId}/cancel`

요청:

```json
{
  "reason": "SELF_CIRCUMSTANCES"
}
```

허용 사유는 `SELF_CIRCUMSTANCES`, `MUTUAL_AGREEMENT`, `COUNTERPART_UNREACHABLE`,
`CONDITION_MISMATCH`다. 선택된 요청의 원래 판매자와 구매자만 취소할 수 있다.

성공 응답은 requestId, roomId, listingId, 요청·거래·판매글 상태와 최초 cancelledById, cancelReason,
cancelledAt을 포함한다. 최초 취소는 INTERNAL/PENDING 이전 취소, 요청 `CANCELLED/CANCELLED`, 판매글
`RESERVED→ACTIVE`, 감사 기록을 같은 트랜잭션에서 수행한다. 다른 PENDING 요청과 채팅·제품·이력은 변경하지 않는다.

동일 당사자의 반복 요청은 최초 취소 메타데이터를 유지한 채 200으로 응답한다. 현재 판매글 상태를 조회해 반환하므로 이후 새
예약이 생겨도 판매글을 다시 ACTIVE로 바꾸지 않는다. PENDING 취소인 `CANCELLED/NULL`이나 완료·불일치 상태는 409다.

## 변경 확인

### `GET /api/v1/trades/{tradeId}/change-reviews`

- 원래 양 당사자가 조회할 수 있다.
- `page` 기본값은 0, `size` 기본값은 20이며 최대 100이다.
- eventId 오름차순 item, 페이지 정보, unreviewedCount, 현재 transactionStatus를 반환한다.
- item은 eventId, factsVersion, 공개 가능한 changeFacts 사본, decision, reviewedAt을 제공한다.

### `POST /api/v1/trades/{tradeId}/change-reviews/{eventId}`

KEEP 요청:

```json
{
  "decision": "KEEP"
}
```

CANCEL 요청:

```json
{
  "decision": "CANCEL",
  "reason": "CONDITION_MISMATCH"
}
```

선택 구매자만 응답할 수 있다. KEEP는 해당 사건의 확인일 뿐 완료 확인 버전을 채우거나 거래를 완료하지 않는다. CANCEL은
review 결정과 예약 취소를 같은 트랜잭션으로 수행한다. 같은 결정 재시도는 기존 결과를 반환하고 다른 결정으로 변경하면 409다.
이미 다른 경로에서 거래가 취소된 뒤 미응답 review를 새로 결정하지 않는다.

## 내부 변경 등록

`ReservationChangeService.recordChange(productId, purchaseRequestId, eventId, factsVersion, changeFacts)`는 history/product
원천이 같은 트랜잭션에서 호출할 내부 경계다. 공개 생성 API는 없다.

- 원천은 같은 제품의 예약 중 새 핵심 사건과 공개 허용 필드만 전달해야 한다.
- 거래는 제품→판매글→구매 요청 순으로 잠그고 RESERVED 연결을 재검증한다.
- `(purchase_request_id, event_id)`가 처음이면 사본을 저장하고 양측 완료 확인 버전을 NULL로 초기화한다.
- 같은 사본 재전송은 기존 값을 유지한다. 다른 factsVersion·사본으로 덮어쓰면 409다.
- `requireNoUnreviewedChanges()`는 P4가 재사용할 보조 가드이며 P4 전체 완료 조건을 대신하지 않는다.

## 판매 중지 연결

`TradeListingCanceller`는 R07 `ListingTradeCanceller`를 구현한다. 상위 서비스가 판매글을 CANCELLED로 flush한 뒤 호출하므로
현재 DB 상태가 CANCELLED여도 변경 전 Command 상태를 기준으로 거래를 정리한다.

- 변경 전 ACTIVE: RESERVED 거래가 없음을 확인하고 모든 PENDING 요청을 `CANCELLED/NULL`로 정리한다.
- 변경 전 RESERVED: 유일 예약 거래·INTERNAL/PENDING 이전을 취소하고 나머지 PENDING도 정리한다.
- 판매글을 ACTIVE로 복구하거나 요청·채팅·review·제품 이력을 삭제하지 않는다.
- RESERVED 판매 중지는 Command reason이 네 예약 취소 enum 문자열과 정확히 일치할 때만 처리한다. R07 자유문을 임의 분류하지
  않으며, UI/공유 Command 확장 전까지 그 외 값은 `SALE_007`로 거부한다.
- `ListingHistoryPrivatizer` 정식 공급이 없으면 기존 R07 상위 서비스가 판매 중지 전체를 계속 차단한다.

## 오류

| 상황 | 상태·코드 |
|---|---|
| 필수값 누락·잘못된 enum·KEEP reason 포함 | `400 / COMMON_001` |
| 인증 없음 | `401 / AUTH_001` |
| 거래 당사자지만 review 응답 역할이 판매자 | `403 / AUTH_002` |
| 대상 없음 또는 비당사자 | `404 / COMMON_002` |
| 상태·연결·결정 충돌 | `409 / TRADE_001` |
| 판매 중지 RESERVED 사유 계약 또는 필수 연동 미확정 | `503 / SALE_007` |

쿠키 인증 요청의 CSRF 처리는 기존 공통 보안 계약을 그대로 따른다.
