# P2 Stage B 연결 결과

## 현재 판정

**B 부분 연결 완료 / ownership 제공 대기**

정식 판매글 연결과 제공된 인증·채팅 경계는 격리 PostgreSQL·Redis에서 검증했다. 정식 ownership Writer와 Migration은 원본 저장소, 원격 브랜치, 공개 PR 어디에도 제공되지 않아 임의 구현하지 않았다. 따라서 기능 플래그는 기본 `false`를 유지하며 B 전체 완료나 정식 수락 경로 완료로 판정하지 않는다.

## 재검수 반영

- Redis `FLUSHDB`는 `P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true`를 명시한 경우에만 실행한다. 이 값은 다른 데이터가 없는 전용·폐기 가능한 Redis 인스턴스임을 실행자가 확인한 뒤 설정한다.
- 보호 조건이 없거나 `false`이면 HTTP/PostgreSQL 시험은 Redis 초기화 전에 실패한다. 보호 조건 자체는 외부 자원 없이 실행되는 `PurchaseRequestHttpPostgresSafetyTest`로 검증한다.
- 일반 전체 검사는 `P2_TRADE_TEST_ENABLED=false`로 전환하고 접속 환경변수를 제거한 뒤 실행하도록 재현 절차를 분리했다.
- 정식 ownership Writer·Migration, 일반 Flyway 편입, legacy 데이터 검증, 공유 환경 적용은 외부 제공 또는 승인이 필요한 잔여 항목으로 유지한다.

### 반영 후 검증 (2026-10-23)

- `PurchaseRequestHttpPostgresSafetyTest`: 통과. 명시 승인 `true`만 허용하고 누락·`false`를 거부함을 확인했다.
- 일반 전체 검사: tests 338, failures 0, errors 0, skipped 22. `spotlessCheck test build --rerun-tasks --no-build-cache`가 성공했다.
- 첫 전체 검사에서 기존 `ChatRepositoryIntegrationTest.participantRoomPageUsesLatestActivityOrder`가 시간 정렬 경합으로 1회 실패했으나, 해당 시험 단독 재실행과 동일 전체 명령 재실행은 통과했다. 이번 변경 범위에는 포함하지 않았다.
- 폐기 가능한 PostgreSQL·Redis를 새로 준비하지 않았으므로 외부 전용 22건은 재실행하지 않았다. 기존 전용 실행 결과와 이번 일반 검사 결과를 구분한다.

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
- PostgreSQL 통합 테스트에서 운영에 없는 `listings.current_owner_id` 보조 컬럼을 제거했다.
- HTTP/소켓 회귀는 대체 빈 없이 production `JdbcChatListingReader`를 사용한다.
- 기존 로그인 API, Access·Refresh 쿠키, CSRF, Redis 회전·차단 구현을 거래 API와 연결해 검증한다.

## 검증표

| ID | 상태 | 필요한 증거 | 확보한 증거 | 결과 |
|---|---|---|---|---|
| B-01 | PASS | 사전 점검 | Git/PR/SHA 확인 | PR #32 병합 및 최신 `main` 기준 확인 |
| B-02 | PASS | ADAPTER-PG | UNIT, ADAPTER-PG, HTTP 부분 연결 | 정식 판매글 단건·벌크·잠금·보관 제품·현재 소유자·예약 검증 |
| B-03 | BLOCKED | E2E-PG | 테스트 fixture만 사용 | 정식 ownership Writer·DDL·제약 미제공 |
| B-04 | BLOCKED | ADAPTER-PG fresh/upgrade | pending DDL 격리 시험 | 공유 DB 이력과 ownership 선행 Migration 미확정 |
| B-05 | PARTIAL | E2E-PG | 실제 인증 경계 + HTTP 부분 연결 | 로그인 쿠키·CSRF·Refresh 회전·재사용 거부·로그아웃 차단 통과, 정식 ownership 미연결 |
| B-06 | PARTIAL | 전체 검사·근거표 | UNIT, ADAPTER-PG, A 회귀, HTTP/소켓 부분 연결 | 가능한 회귀와 보고 완료, B-03·04·07 잔여 |
| B-07 | BLOCKED | SHARED | 없음 | 공유 환경 적용 승인과 구성 미제공 |

## 재현 명령과 결과

작업 위치는 `<PROJECT_ROOT>`, 실행 기준은 `0563d423381bc987009a4d0653d27dd395d7d070` 기반 보강 작업 트리다. 비밀값은 실제 실행 시 로컬 임시 값으로 치환한다.

```powershell
$env:P2_TRADE_TEST_ENABLED='true'
$env:P2_TRADE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:<PG_PORT>/pastport_stageb_review'
$env:P2_TRADE_TEST_DB_USER='postgres'
$env:P2_TRADE_TEST_DB_PASSWORD='<REDACTED>'
$env:P2_TRADE_TEST_REDIS_HOST='127.0.0.1'
$env:P2_TRADE_TEST_REDIS_PORT='<REDIS_PORT>'
$env:P2_TRADE_TEST_REDIS_FLUSH_ALLOWED='true' # 전용·폐기 가능한 Redis에서만 설정
.\gradlew.bat test --tests org.example.springtestci.trade.repository.PurchaseRequestPostgresIntegrationTest --tests org.example.springtestci.trade.controller.PurchaseRequestHttpPostgresTest --rerun-tasks --no-build-cache
```

- PostgreSQL/Redis 전용: tests 22, failures 0, errors 0, skipped 0.
- 상세: `PurchaseRequestPostgresIntegrationTest` 15건, `PurchaseRequestHttpPostgresTest` 7건.
- 결과: `build/test-results/test/TEST-org.example.springtestci.trade.repository.PurchaseRequestPostgresIntegrationTest.xml`, `build/test-results/test/TEST-org.example.springtestci.trade.controller.PurchaseRequestHttpPostgresTest.xml`.

```powershell
$env:P2_TRADE_TEST_ENABLED='false'
Remove-Item Env:P2_TRADE_TEST_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:P2_TRADE_TEST_DB_USER -ErrorAction SilentlyContinue
Remove-Item Env:P2_TRADE_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:P2_TRADE_TEST_REDIS_HOST -ErrorAction SilentlyContinue
Remove-Item Env:P2_TRADE_TEST_REDIS_PORT -ErrorAction SilentlyContinue
Remove-Item Env:P2_TRADE_TEST_REDIS_FLUSH_ALLOWED -ErrorAction SilentlyContinue
.\gradlew.bat spotlessCheck test build --rerun-tasks --no-build-cache
```

- 일반 전체 검사: tests 336, failures 0, errors 0, skipped 22.
- skipped 22건은 외부 환경변수가 필요한 위 PostgreSQL 전용 15+7건이며, 별도 전용 실행에서 전부 통과했다.
- 결과: `build/reports/tests/test/index.html`, `build/test-results/test/`.
- 첫 전체 검사에서 패치 구간 줄바꿈 혼합을 Spotless가 탐지해 중단됐고, `gradlew.bat spotlessApply` 후 동일 명령을 강제 재실행해 통과했다.
- ownership 관련 성공은 테스트 fixture를 사용하므로 정식 ownership E2E로 집계하지 않는다.

## B-T01~22 근거

| ID | 상태 | 실제 근거·미확보 사유 |
|---|---|---|
| B-T01 | 확보 | `9e80cae`/PR #32/기능 플래그/production 판매글·채팅 빈 확인 |
| B-T02 | 대기 | 거래 DDL이 pending 경로여서 일반 Flyway fresh 미충족 |
| B-T03 | 대기 | 정식 upgrade·재기동·validate 및 공유 이력 미확인 |
| B-T04 | 확보 | `readsProductionListingStateFromProductsAndListings`, `JdbcPublishedListingAccessTest` |
| B-T05 | 부분 | 생성·재사용·필터·취소·거절 A 회귀 통과, 정식 ownership 미연결 |
| B-T06 | 부분 | 수락 세 상태는 테스트 ownership fixture로만 확인 |
| B-T07 | 부분 | 실제 제품 소유자 불일치·외부 승인 필드 불변 회귀, 정식 Writer 대기 |
| B-T08 | 부분 | 동시 수락 PostgreSQL 회귀, ownership fixture 사용 |
| B-T09 | 확보 | 동일 방 동시 생성과 수락·취소 경합 PostgreSQL 회귀 |
| B-T10 | 부분 | 409/`TRADE_001`·기존 행 보존·롤백 확인, ownership fixture 사용 |
| B-T11 | 부분 | DB 트리거 실패와 예약 후 실패 전체 롤백·500 확인, ownership fixture 사용 |
| B-T12 | 부분 | `usesLoginCookiesCsrfRefreshRotationAndLogoutBlocklistForTradeApi` 정상 쿠키+CSRF 거래 생성 성공 |
| B-T13 | 부분 | 같은 메서드에서 CSRF 누락·불일치 403 및 거래 행 0건 확인 |
| B-T14 | 부분 | Refresh 회전·소비 토큰 재사용 401·갱신 후 접근·로그아웃 Access/Refresh 401 확인. 만료 토큰은 기존 보안 단위 회귀 |
| B-T15 | 부분 | 구매자 수락 거부·제품 소유자 불일치 등 A 회귀. 정식 ownership 권한 경계 대기 |
| B-T16 | 부분 | `reservationBlocksNewChatRoomButKeepsExistingRoomMessaging`가 production `JdbcChatListingReader`로 신규 방 차단·POST/GET·읽기 위치·STOMP 수신 확인 |
| B-T17 | 대기 | legacy 채팅 데이터와 초안 ID 판정 대상 환경 미제공 |
| B-T18 | 확보 | 기본 off·on·필수 빈 누락 구성 시험, production 대체 빈 없음 확인 |
| B-T19 | 부분 | pending DDL 제약 회귀 통과, 일반 Flyway 편입 전 |
| B-T20 | 확보 | 강제 일반 전체 검사와 전용 PostgreSQL/Redis 검사 분리 통과 |
| B-T21 | 확보 | 본 결과·Migration 계획·자원 사용보고서 보정 |
| B-T22 | 대기 | 공유 환경 승인·구성 미제공 |

## 잔여 작업 인계

| 항목 | 현재 상태 | 완료 조건 | 현재 조치 |
|---|---|---|---|
| Redis 시험 자원 보호 | 반영 | 명시 승인 없이는 `FLUSHDB` 미실행 | 보호 조건·단위 테스트·재현 명령 추가 |
| 일반 검사 환경 전환 | 반영 | 외부 시험 비활성화 및 접속값 제거 | 일반 검사 블록에 전환 명령 추가 |
| 정식 ownership Writer·DDL | 외부 제공 대기 | production 서비스·Migration·제약·트랜잭션 계약 제공 | fixture를 production으로 승격하지 않음 |
| 거래 DDL 일반 Flyway 편입 | 선행 계약 대기 | ownership 순서와 공유 DB 이력 확정 후 fresh/upgrade 통과 | pending 경로 유지 |
| legacy 채팅방 연결 | 대상 데이터 대기 | 실제 `chat_rooms.listing_id`의 정식 판매글 참조 확인 | 자동 보정하지 않음 |
| 공유 환경 적용·smoke | 승인·구성 대기 | 승인된 환경에서 Migration·API·채팅 smoke 완료 | 로컬 검증과 구분 유지 |

## 정식 ownership 제공 요청

필요한 제공물은 다음과 같다.

- `InternalTransferPendingWriter.createPending(Command)`에 연결할 production 서비스 또는 저장 경계
- `productId`, `purchaseRequestId`, `fromUserId`, `toUserId`, `INTERNAL`, `PENDING` 입력 매핑
- `ownership_transfers` Migration과 `purchase_requests` FK 적용 순서
- `purchase_request_id` 1:1 및 제품별 `PENDING` 최대 하나를 보장하는 실제 제약명
- 거래 Service와 같은 DataSource·트랜잭션 참여 및 실패 전파 계약
- 검증 대상 브랜치·PR·SHA와 제공 담당자

제공 전에는 테스트 Writer를 production으로 이동하거나 새로운 ownership 테이블·API를 만들지 않는다.
