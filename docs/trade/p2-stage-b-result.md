# P2 Stage B 연결 결과

## 현재 판정

**B 로컬 연결 완료 / 공유 환경 적용 대기**

정식 판매글·채팅·인증 경계와 production ownership PENDING Writer를 연결했다. 거래·ownership DDL은 일반 Flyway 경로에 편입했고 격리 PostgreSQL에서 fresh·upgrade·legacy ID 격리·validate를 검증했다. 승인된 공유 환경의 Migration 이력과 실제 legacy 데이터는 확인하지 않았으므로 기능 플래그는 기본 `false`를 유지하며 B 전체 운영 완료로 판정하지 않는다.

## 기준 확인

| 항목 | 확인 결과 |
|---|---|
| 작업 브랜치 | `feature/trade-p2-integration` |
| 선행 보강 SHA | `e5284ae3cf68e006561ffd99cf27b4844cec28ef` |
| 병합한 원본 `main` | `1f50021`, PR #33~38 포함 |
| 정식 판매글 | `listings`, `products.owner_id`, production JDBC 어댑터 사용 |
| 정식 ownership | `JdbcInternalTransferPendingWriter`, `ownership_transfers` Migration 사용 |
| 기능 플래그 | 공유 환경 검증 전까지 기본 `false` 유지 |

## 변경 내용

- 최신 `upstream/main`을 병합해 현재 판매글·문서·보안 코드 기준으로 맞췄다.
- pending 거래 DDL을 `V2026102301__create_purchase_requests.sql`로 일반 Flyway 경로에 편입했다.
- `V2026102302__create_ownership_transfers.sql`에 FK, 유형·상태·내부/외부 형태 제약을 추가했다.
- `V2026102303__add_trade_pending_unique_indexes`가 PostgreSQL 부분 고유 인덱스 3개를 생성한다.
- `JdbcInternalTransferPendingWriter`를 추가하고 제품별 PENDING 이전 충돌을 `TRADE_001`로 변환한다.
- PostgreSQL·HTTP 시험의 테스트 Writer와 수동 ownership 테이블 생성을 제거해 production Writer·Migration을 직접 사용한다.
- `TradeMigrationPostgresTest`가 fresh와 legacy 데이터 포함 upgrade 경로를 검증한다.

## 검증표

| ID | 상태 | 확보한 증거 | 결과 |
|---|---|---|---|
| B-01 | PASS | 최신 원격 main·의존 모듈 재조사 | `1f50021` 반영, 별도 ownership 제공 없음 확인 |
| B-02 | PASS | production 판매글 JDBC·PostgreSQL 회귀 | 단건·벌크·잠금·현재 소유자·예약 검증 |
| B-03 | PASS | production ownership Writer·DDL·제약 | 수락 시 PENDING 생성, 충돌 변환, 실패 전체 롤백 |
| B-04 | PASS | PostgreSQL fresh·upgrade·validate | 일반 Flyway 편입과 버전 순서 검증 |
| B-05 | PASS | 실제 인증 경계 + HTTP + production ownership | 쿠키·CSRF·Refresh 회전·차단과 거래 수락 검증 |
| B-06 | PARTIAL | 전체 검사·로컬 근거표 | 로컬 연결 완료, 실제 legacy·공유 환경 잔여 |
| B-07 | BLOCKED | 공유 환경 증거 없음 | 적용 승인과 접속 구성 필요 |

## 실행 결과 (2026-10-23)

작업 기준은 `e5284ae`에 `upstream/main` `1f50021`을 병합한 보강 작업 트리다.

### PostgreSQL·Redis 전용

```powershell
$env:P2_TRADE_TEST_ENABLED='true'
$env:P2_TRADE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:<PG_PORT>/pastport_stageb'
$env:P2_TRADE_TEST_DB_USER='postgres'
$env:P2_TRADE_TEST_DB_PASSWORD='<REDACTED>'
$env:P2_TRADE_TEST_REDIS_HOST='127.0.0.1'
$env:P2_TRADE_TEST_REDIS_PORT='<REDIS_PORT>'
$env:P2_TRADE_TEST_REDIS_FLUSH_ALLOWED='true'
.\gradlew.bat test `
  --tests org.example.springtestci.trade.repository.TradeMigrationPostgresTest `
  --tests org.example.springtestci.trade.repository.PurchaseRequestPostgresIntegrationTest `
  --tests org.example.springtestci.trade.controller.PurchaseRequestHttpPostgresTest `
  --rerun-tasks --no-build-cache
```

- tests 24, failures 0, errors 0, skipped 0
- `TradeMigrationPostgresTest` 2건, repository 15건, HTTP·인증·채팅 7건
- 전용·폐기 가능한 Redis endpoint 확인 후에만 `FLUSHDB` 승인값을 설정했다.

### 일반 전체 검사

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

- tests 356, failures 0, errors 0, skipped 24
- skipped 24건은 위 외부 전용 3개 클래스이며 별도 실행에서 모두 통과했다.

## B-T01~22 근거

| ID | 상태 | 실제 근거·미확보 사유 |
|---|---|---|
| B-T01 | 확보 | 최신 main, 기능 플래그, production 판매글·채팅·ownership 빈 확인 |
| B-T02 | 확보 | 일반 Flyway fresh에서 거래·ownership 테이블과 PostgreSQL 인덱스 확인 |
| B-T03 | 확보 | `V2026102002` upgrade, validate, 최신 재실행 0건 확인 |
| B-T04 | 확보 | production 판매글 상태·소유자·잠금 조회 회귀 |
| B-T05 | 확보 | 생성·재사용·필터·취소·거절과 production ownership 연결 회귀 |
| B-T06 | 확보 | 수락 시 요청 ACCEPTED, 거래·판매글 RESERVED, ownership PENDING 검증 |
| B-T07 | 확보 | 실제 제품 소유자 불일치 차단과 내부 이전 외부 승인 필드 불변 검증 |
| B-T08 | 확보 | production Writer를 사용한 동시 수락 PostgreSQL 회귀 |
| B-T09 | 확보 | 동일 방 동시 생성과 수락·취소 경합 회귀 |
| B-T10 | 확보 | 제품별 PENDING 제약 위반을 409/`TRADE_001`로 변환하고 기존 행 보존 |
| B-T11 | 확보 | DB 트리거 실패와 Writer·예약 후 실패의 전체 롤백 |
| B-T12 | 확보 | 정상 쿠키+CSRF 거래 생성 성공 |
| B-T13 | 확보 | CSRF 누락·불일치 403 및 거래 행 미생성 |
| B-T14 | 확보 | Refresh 회전·재사용 거부·로그아웃 Access/Refresh 차단 |
| B-T15 | 확보 | 구매자 수락 거부·현재 소유자 불일치·거래 당사자 경계 |
| B-T16 | 확보 | 예약 후 신규 방 차단, 기존 HTTP·STOMP 채팅 유지 |
| B-T17 | 부분 | 격리 upgrade fixture에서 legacy ID 보존·신규 ID 격리 확인, 공유 실데이터 미확인 |
| B-T18 | 확보 | 기본 off·on 구성과 production 빈 연결 확인 |
| B-T19 | 확보 | 일반 Flyway DDL의 FK·CHECK·UNIQUE·부분 고유 제약 회귀 |
| B-T20 | 확보 | 전용 24건과 일반 356건 분리 실행 성공 |
| B-T21 | 확보 | 결과·Migration 계획·자원 사용보고서 갱신 |
| B-T22 | 대기 | 공유 환경 승인·구성 미제공 |

## 잔여 작업 인계

| 항목 | 현재 상태 | 완료 조건 |
|---|---|---|
| 공유 Migration 이력 확인 | 승인 대기 | 동일 버전·수동 DDL 흔적 읽기 전용 확인 |
| 실제 legacy 채팅 연결 | 대상 데이터 대기 | `chat_rooms.listing_id`와 정식 판매글·초안 흔적 분류 |
| 공유 환경 적용·smoke | 승인·구성 대기 | 백업 승인 후 Migration·구매 요청·수락·채팅 smoke |
| 기능 기본 활성화 | 보류 | 공유 검증과 팀 승인 후 별도 결정 |

수동 DDL이나 우연한 ID 일치는 자동 보정하지 않는다. 공유 환경 승인 전 `repair`, `baseline`, `clean` 또는 기능 기본 활성화를 수행하지 않는다.
