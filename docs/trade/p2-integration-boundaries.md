# P2 외부 연동 경계

## 게시 판매글

`PublishedListingAccess`는 게시글 ID, 제품 ID, 고정 판매자, 현재 소유자, 상태를 제공한다. 구매 생성·수락에서는 같은 DataSource와 트랜잭션에 참여하는 잠금 조회가 필요하며, 수락 시 ACTIVE에서 RESERVED로 조건부 변경해야 한다.

현재 저장소의 `listing_drafts`는 정식 게시 판매글이 아니므로 운영 어댑터로 연결하지 않았다. A단계 PostgreSQL 검증에서는 별도 `listings` fixture 테이블과 테스트 전용 JDBC 어댑터를 사용했다.

## 내부 이전

`InternalTransferPendingWriter` 입력은 다음과 같다.

- `productId`: 수락한 게시글의 제품
- `purchaseRequestId`: 수락한 구매 요청
- `fromUserId`: 검증된 판매자이자 현재 소유자
- `toUserId`: 선택한 방의 문의자
- `transferType`: `INTERNAL`
- `status`: `PENDING`

Writer는 거래 Service와 같은 트랜잭션에 참여해야 한다. 생성 실패 시 요청과 게시글 변경도 롤백된다. 제품 소유자 변경, 이전 완료, 토큰 발급, 승인 시각 기록은 수행하지 않는다.

## 스키마

`purchase_requests` DDL 원본은 `src/main/resources/db/pending/trade-p2`에 있다. 기본 Flyway 위치에는 포함하지 않았고, A단계 전용 PostgreSQL 스키마에서만 기본 Migration과 fixture 테이블 적용 후 명시적으로 실행했다.

## B단계 연결 조건

1. 정식 `listings` 모델의 ID·제품·판매자·현재 소유자·상태 및 잠금 순서 확정
2. 기존 채팅방 `listing_id`와 정식 게시글 ID의 연결 보장
3. 정식 `ownership_transfers` Writer와 제품별 진행 중 이전 제약 확정
4. 두 외부 구현이 거래 JDBC 저장소와 같은 DataSource·트랜잭션에 참여함을 검증
5. 선행 Migration 버전 순서 확인 후 pending DDL의 기본 경로 편입 승인

위 조건 전에는 기능 플래그를 기본 활성화하지 않는다.
