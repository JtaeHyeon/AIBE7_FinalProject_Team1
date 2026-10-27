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

## 인증·CSRF 계약

- 로그인은 `POST /api/auth/login`이며, 성공 시 토큰을 응답 본문에 노출하지 않고 HttpOnly
  `access_token`(경로 `/`)과 `refresh_token`(경로 `/api/auth`) 쿠키로 발급한다.
- CSRF 토큰은 서버 응답에서 읽기 가능한 `XSRF-TOKEN` 쿠키로 발급된다. 쿠키 인증으로 상태를 변경하는 요청은 이 값을
  `X-XSRF-TOKEN` 헤더에 함께 보내야 하며, 누락하거나 값이 다르면 현재 공통 보안 경로에서 `403`으로 거부된다.
- 구매 요청 Controller는 요청 본문의 사용자 ID를 받지 않는다. 검증된 Access JWT의 숫자형 `sub`를
  `CurrentUserIdResolver`로 해석해 요청자를 식별하며, 인증 정보가 없거나 유효하지 않으면 `401`이다.
- 명시적 `Authorization: Bearer <access-token>` 헤더와 `access_token` 쿠키가 함께 있으면 명시적 헤더를 우선한다.
  쿠키 필터는 Authorization 헤더가 없는 API 요청에만 쿠키 값을 Bearer 헤더로 보완한다.
- `POST /api/auth/refresh`는 `refresh_token` 쿠키를 회전시키고 새 Access·Refresh 쿠키를 발급한다. 이전 Refresh
  Token의 재사용은 기존 시험에서 `401`로 확인했다.
- `POST /api/auth/logout`은 Refresh Token을 폐기하고 사용 중인 Access Token을 차단한 뒤 두 인증 쿠키를 만료한다.
  로그아웃 뒤 해당 Access 쿠키의 보호 API 접근과 폐기된 Refresh Token 사용은 기존 시험에서 `401`로 확인했다.
- 명시적 Bearer 요청은 Spring Security의 현재 설정에 따라 CSRF 검사 대상에서 제외된다. 쿠키 인증 요청은 CSRF
  검사를 통과한 뒤 Access 쿠키가 Bearer 헤더로 변환된다.

브라우저 흐름은 먼저 `/` 등 서버 응답을 받아 최신 `XSRF-TOKEN` 쿠키를 확보한 뒤 상태 변경 요청에 같은 값을
`X-XSRF-TOKEN`으로 보낸다. 위 내용은 현재 인증 지원 계약이며, 정식 ownership Writer 미연결 상태와는 별개다.
