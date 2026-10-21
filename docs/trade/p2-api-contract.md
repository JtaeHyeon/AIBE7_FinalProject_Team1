# P2 구매 요청·예약 API 계약

## 기능 등록

- 기본값: `app.features.trade-purchase-requests.enabled=false`
- 활성화 시 `PublishedListingAccess`, `InternalTransferPendingWriter` 구현이 모두 필요하다.
- 일반 실행에는 정식 게시글·내부 이전 대체 구현이나 no-op 구현을 등록하지 않는다.

## API

| Method | URI | 성공 |
|---|---|---|
| POST | `/api/v1/market/listings/{listingId}/purchase-requests` | 신규 201, 동일 방 PENDING 재사용 200 |
| GET | `/api/v1/purchase-requests` | 200 |
| GET | `/api/v1/purchase-requests/{requestId}` | 200 |
| POST | `/api/v1/purchase-requests/{requestId}/cancel` | 200 |
| POST | `/api/v1/purchase-requests/{requestId}/accept` | 201 |
| POST | `/api/v1/purchase-requests/{requestId}/reject` | 200 |

생성 본문은 `{ "roomId": positive long }`이다. 사용자·판매자·상태 값은 입력받지 않고 검증된 JWT 주체에서 행위자를 결정한다.

목록의 `direction`은 `SENT` 또는 `RECEIVED`이며 필수다. `status`, `roomId`는 선택이고 `page` 기본값은 0, `size` 기본값은 20이며 최대 100이다. 정렬은 `requestId DESC`다.

## 응답

단건 응답은 `requestId`, `roomId`, `listingId`, `requestStatus`, `transactionStatus`, `listingStatus`만 제공한다. 목록은 `items`, `page`, 선택적 `roomContext`로 구성한다. 종료된 요청의 게시글이 명시적으로 없으면 `listingStatus`는 `null`일 수 있다.

## 상태와 권한

- 문의자는 자기 방에 PENDING 요청을 만들고 수락 전 취소할 수 있다.
- 판매자는 자기 판매 방의 PENDING 요청을 수락하거나 거절할 수 있다.
- 비참여자의 방·요청은 404로 숨긴다. 참여자이지만 역할이 다른 행위는 403이다.
- 방과 URI 게시글 불일치, ACTIVE·소유자 불일치, 종료 요청 재처리, 예약 충돌은 409다.
- 수락은 요청 `ACCEPTED`, 거래 `RESERVED`, 게시글 `RESERVED`, 내부 이전 `INTERNAL/PENDING`을 한 트랜잭션으로 처리한다.
- 선택하지 않은 다른 PENDING 요청은 유지한다.

현재 인증은 공통 Bearer JWT 경로를 그대로 사용한다. 쿠키·CSRF 최종 계약은 B단계 대기 사항이다.
