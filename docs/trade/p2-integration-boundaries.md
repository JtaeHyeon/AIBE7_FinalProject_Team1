# P2 외부 연동 경계

## 게시 판매글

`PublishedListingAccess`는 게시글 ID, 제품 ID, 고정 판매자, 현재 소유자, 상태를 제공한다. 구매 생성·수락에서는 같은 DataSource와 트랜잭션에 참여하는 잠금 조회가 필요하며, 수락 시 ACTIVE에서 RESERVED로 조건부 변경해야 한다.

`JdbcPublishedListingAccess`가 정식 `listings`와 `products`에 연결된다. 단건·벌크 조회는 `products.archived_at`이 있는 제품을 `CANCELLED`로 동일하게 해석하고, 현재 소유자는 `products.owner_id`에서 읽는다. 수락 잠금 조회는 판매글에서 제품 ID를 식별한 뒤 제품 행과 판매글 행 순으로 잠그며, 예약은 `ACTIVE` 한 행만 `RESERVED`로 조건부 변경한다. 테스트용 소유자 컬럼이나 판매글 fixture 빈은 production 연결에 사용하지 않는다.

## 내부 이전

`InternalTransferPendingWriter` 입력은 다음과 같다.

- `productId`: 수락한 게시글의 제품
- `purchaseRequestId`: 수락한 구매 요청
- `fromUserId`: 검증된 판매자이자 현재 소유자
- `toUserId`: 선택한 방의 문의자
- `transferType`: `INTERNAL`
- `status`: `PENDING`

Writer는 거래 Service와 같은 트랜잭션에 참여해야 한다. 생성 실패 시 요청과 게시글 변경도 롤백된다. 제품 소유자 변경, 이전 완료, 토큰 발급, 승인 시각 기록은 수행하지 않는다.

`JdbcInternalTransferPendingWriter`가 이 계약을 구현한다. 제품별 PENDING 유일성 위반은 `TRADE_001`로 변환하고, 그 밖의 DB 오류는 그대로 전파해 거래 요청·판매글 예약과 함께 롤백한다. PostgreSQL 통합·HTTP 시험은 더 이상 테스트 Writer를 등록하지 않고 이 production 구현을 사용한다.

## 스키마

`purchase_requests`와 `ownership_transfers`는 각각 일반 Flyway 경로의 `V2026102301`, `V2026102302`에서 생성한다. `V2026102303` Java Migration은 PostgreSQL에 PENDING 방, RESERVED 판매글, 제품별 PENDING 이전의 부분 고유 인덱스를 생성한다.

## B단계 연결 조건

1. 정식 `listings` 모델과 `products.owner_id`를 사용하는 운영 어댑터 및 잠금 순서 확정 — 완료
2. 기존 채팅방 `listing_id`와 정식 게시글 ID의 우연한 충돌 방지 — 격리 upgrade fixture 완료, 공유 실데이터 확인 대기
3. 정식 `ownership_transfers` Writer와 제품별 진행 중 이전 제약 확정 — 완료
4. 판매글·거래·ownership JDBC 구현의 동일 DataSource·트랜잭션 참여 검증 — 완료
5. 거래·ownership DDL 일반 Flyway 편입과 fresh/upgrade/validate — 완료

공유 환경의 Migration 이력과 legacy 데이터를 읽기 전용으로 확인하고 승인된 smoke를 마치기 전에는 기능 플래그를 기본 활성화하지 않는다.
