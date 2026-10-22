# P2 Stage B 연결 결과

## 현재 판정

**B 부분 연결 완료 / ownership 제공 대기**

정식 판매글 연결은 구현하고 격리 PostgreSQL에서 검증했다. 정식 ownership Writer와 Migration은 원본 저장소, 원격 브랜치, 공개 PR 어디에도 제공되지 않아 임의 구현하지 않았다. 따라서 기능 플래그는 기본 `false`를 유지하며 B 전체 완료나 정식 수락 경로 완료로 판정하지 않는다.

## 기준 확인

| 항목 | 확인 결과 |
|---|---|
| 원본 저장소 | `prgrms-aibe-devcourse/AIBE7_FinalProject_Team1` |
| 최신 원본 `main` | `9e80cae604d2856dc3955cba6ab672fb8c01d541` |
| A단계 SHA | `055c4514a8b93ba3d4bcf11c9e8d217c403a751e` |
| PR #32 | 2026-10-21 19:24:34 KST 병합, merge SHA `9e80cae` |
| 작업 브랜치 | `feature/trade-p2-integration`, `upstream/main` 기준 |
| 정식 판매글 | `Listing`, `listings`, `products.owner_id` 제공 |
| 정식 ownership | 미제공. 패키지 자리표시자만 존재하고 관련 공개 PR 없음 |

## 변경 내용

- `JdbcPublishedListingAccess`를 추가해 `PublishedListingAccess`를 정식 `listings`·`products`에 연결했다.
- 보관 제품 상태는 기존 `JdbcChatListingReader`와 동일하게 `CANCELLED`로 해석한다.
- 수락 잠금 순서를 제품 → 판매글 → 구매 요청으로 맞췄다. 제품과 판매글 잠금은 production 어댑터, 구매 요청 잠금은 기존 거래 저장소가 수행한다.
- `reserve()`는 `ACTIVE`인 판매글 한 행만 `RESERVED`로 바꾸며 그 외에는 `TRADE_001` 충돌로 실패한다.
- PostgreSQL 통합 테스트의 판매글 fixture 구현은 production 어댑터 위임 방식으로 교체했고, HTTP 테스트의 판매글 대체 빈은 제거했다.

## 검증표

| ID | 상태 | 증거 등급 | 결과 |
|---|---|---|---|
| B-01 | PASS | SHARED | PR #32 병합 및 최신 `main` 기준 확인 |
| B-02 | PASS | UNIT, ADAPTER-PG, E2E-PG | 정식 판매글 조회·잠금·벌크 상태·예약과 거래 회귀 통과 |
| B-03 | BLOCKED | SHARED | 정식 ownership Writer·DDL·제약 미제공 |
| B-04 | BLOCKED | ADAPTER-PG | pending DDL 시험은 통과했으나 공유 DB Flyway 이력과 ownership 선행 Migration 미확정 |
| B-05 | NOT_RUN | E2E-PG | 실행한 HTTP 시험은 Bearer JWT 경로이며 실제 쿠키 로그인·CSRF 전체 흐름은 이번 범위에서 미검증 |
| B-06 | PARTIAL | UNIT, ADAPTER-PG, E2E-PG | 가능한 회귀와 보고 완료, B-03~05 잔여 |
| B-07 | BLOCKED | SHARED | 공유 환경 적용 승인과 구성 미제공 |

실행 결과:

- `gradlew.bat spotlessApply test --tests ...JdbcPublishedListingAccessTest --tests ...PurchaseRequestFeatureFlagTest` — PASS
- 격리 PostgreSQL 16에서 `PurchaseRequestPostgresIntegrationTest`, `PurchaseRequestHttpPostgresTest` — PASS
- `gradlew.bat spotlessCheck test build` — PASS
- ownership 관련 성공은 테스트 fixture를 사용하므로 정식 ownership 검증으로 집계하지 않는다.

## 정식 ownership 제공 요청

필요한 제공물은 다음과 같다.

- `InternalTransferPendingWriter.createPending(Command)`에 연결할 production 서비스 또는 저장 경계
- `productId`, `purchaseRequestId`, `fromUserId`, `toUserId`, `INTERNAL`, `PENDING` 입력 매핑
- `ownership_transfers` Migration과 `purchase_requests` FK 적용 순서
- `purchase_request_id` 1:1 및 제품별 `PENDING` 최대 하나를 보장하는 실제 제약명
- 거래 Service와 같은 DataSource·트랜잭션 참여 및 실패 전파 계약
- 검증 대상 브랜치·PR·SHA와 제공 담당자

제공 전에는 테스트 Writer를 production으로 이동하거나 새로운 ownership 테이블·API를 만들지 않는다.
