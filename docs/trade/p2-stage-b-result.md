# P2 Stage B 연결 결과

## 현재 판정

**B 부분 연결 완료 / ownership 제공 대기**

최신 `main` 통합과 정식 판매글·인증·채팅 경계는 보존했다. 정식 ownership Writer와 Migration은 원본 저장소, 원격 브랜치, 공개 PR 어디에도 제공되지 않아 임의 구현하지 않았다. 따라서 기능 플래그는 기본 `false`를 유지하며 B 전체 완료나 정식 수락 경로 완료로 판정하지 않는다.

## 이번 리뷰 반영

- `a9fc04b`에서 추가한 production ownership Writer와 공통 ownership DDL은 별도 제안 이력으로만 보존하고 현재 작업 트리에서는 제외했다.
- 거래 DDL은 `db/pending/trade-p2`로 복원해 기본 Flyway 기동으로 공유 DB에 자동 적용되지 않게 했다.
- PostgreSQL·HTTP 시험은 테스트 전용 ownership fixture와 Writer를 다시 사용하며, 그 성공을 정식 ownership E2E로 집계하지 않는다.
- Redis `FLUSHDB`는 `P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true`를 명시한 전용·폐기 가능한 인스턴스에서만 실행한다.
- 정식 ownership 제공, 일반 Flyway 편입, 실제 legacy 데이터 검증, 공유 환경 적용은 외부 제공 또는 승인이 필요한 잔여 항목으로 유지한다.

## 기준 확인

| 항목 | 확인 결과 |
|---|---|
| 작업 브랜치 | `feature/trade-p2-integration` |
| 최신 main 통합 | `7179372`에서 원본 `main` `1f50021` 통합 |
| 보존한 P2 보강 | `e5284ae`까지의 판매글·인증·Redis 안전장치·회귀 보강 |
| 정식 판매글 | `listings`, `products.owner_id`, production JDBC 어댑터 사용 |
| 정식 ownership | 미제공. 패키지 자리표시자와 테스트 fixture만 존재 |
| 기능 플래그 | 정식 의존성과 공유 환경 검증 전까지 기본 `false` 유지 |

## 변경 내용

- `JdbcPublishedListingAccess`의 정식 판매글 단건·벌크·잠금·예약 연결을 유지했다.
- 수락 잠금 순서와 동일 DataSource·Spring 트랜잭션 참여 검증을 유지했다.
- 기존 로그인 API, Access·Refresh 쿠키, CSRF, Redis 회전·차단 회귀를 유지했다.
- `V2026102103__create_purchase_requests.sql`을 pending 위치로 복원했다.
- 테스트 전용 ownership 테이블·Writer와 알려진 제약 충돌 변환을 복원했다.
- 미승인 ownership 운영 구현과 기본 Flyway Migration 시험은 현재 부분 연결 범위에서 제거했다.

## 검증표

| ID | 상태 | 확보한 증거 | 결과 |
|---|---|---|---|
| B-01 | PASS | 최신 main 통합과 의존 모듈 재조사 | `1f50021` 반영, 별도 ownership 제공 없음 |
| B-02 | PASS | production 판매글 JDBC·PostgreSQL 회귀 | 단건·벌크·잠금·현재 소유자·예약 검증 |
| B-03 | BLOCKED | 테스트 fixture 회귀만 가능 | 정식 ownership Writer·DDL·제약 미제공 |
| B-04 | BLOCKED | pending DDL 격리 시험 | 공유 DB 이력과 ownership 선행 Migration 미확정 |
| B-05 | PARTIAL | 실제 인증 경계 + HTTP 부분 연결 | 인증 회귀 통과, 정식 ownership 미연결 |
| B-06 | PARTIAL | 전체 검사·근거표 | 가능한 회귀와 보고 완료, B-03·04·07 잔여 |
| B-07 | BLOCKED | 공유 환경 증거 없음 | 적용 승인과 접속 구성 미제공 |

## 재현 명령

비밀값은 실제 실행 시 전용 로컬 자원의 임시 값으로 치환한다.

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

## 복원 후 실행 결과 (2026-10-23)

- PostgreSQL·Redis 전용: tests 22, failures 0, errors 0, skipped 0.
- 일반 전체 검사: tests 354, failures 0, errors 0, skipped 22.
- 일반 검사는 `spotlessCheck test build --rerun-tasks --no-build-cache`로 실행했다.
- skipped 22건은 외부 환경이 필요한 위 두 전용 클래스이며 별도 실행에서 모두 통과했다.
- `a9fc04b`의 24/356 집계는 복원 후 결과로 재사용하지 않았다.
- 상세 자원 생성·정리 이력은 `p2-stage-b-local-resource-usage.md`에 기록했다.

## B-T01~22 근거

| ID | 상태 | 실제 근거·미확보 사유 |
|---|---|---|
| B-T01 | 확보 | 최신 main, 기능 플래그, production 판매글·채팅 빈 확인 |
| B-T02 | 대기 | 거래 DDL이 pending 경로여서 일반 Flyway fresh 미충족 |
| B-T03 | 대기 | 정식 upgrade·재기동·validate 및 공유 이력 미확인 |
| B-T04 | 확보 | production 판매글 상태·소유자·잠금 조회 회귀 |
| B-T05 | 부분 | 생성·재사용·필터·취소·거절 회귀, 정식 ownership 미연결 |
| B-T06 | 부분 | 수락 세 상태는 테스트 ownership fixture로만 확인 |
| B-T07 | 부분 | 실제 제품 소유자 불일치·외부 승인 필드 불변 회귀, 정식 Writer 대기 |
| B-T08 | 부분 | 동시 수락 PostgreSQL 회귀, ownership fixture 사용 |
| B-T09 | 확보 | 동일 방 동시 생성과 수락·취소 경합 PostgreSQL 회귀 |
| B-T10 | 부분 | 409/`TRADE_001`·기존 행 보존·롤백 확인, ownership fixture 사용 |
| B-T11 | 부분 | DB 트리거 실패와 예약 후 실패 전체 롤백·500 확인, ownership fixture 사용 |
| B-T12 | 부분 | 정상 쿠키+CSRF 거래 생성 성공, 정식 ownership 미연결 |
| B-T13 | 확보 | CSRF 누락·불일치 403 및 거래 행 미생성 |
| B-T14 | 확보 | Refresh 회전·재사용 거부·로그아웃 Access/Refresh 차단 |
| B-T15 | 부분 | 구매자 수락 거부·제품 소유자 불일치, 정식 ownership 권한 경계 대기 |
| B-T16 | 확보 | 예약 후 신규 방 차단과 기존 HTTP·STOMP 채팅 유지 |
| B-T17 | 대기 | legacy 채팅 데이터와 초안 ID 판정 대상 환경 미제공 |
| B-T18 | 확보 | 기본 off·on·필수 빈 누락 구성 시험, production 대체 빈 없음 확인 |
| B-T19 | 부분 | pending DDL 제약 회귀, 일반 Flyway 편입 전 |
| B-T20 | 확보 | 복원 트리 전용 22건과 일반 354건 분리 실행 성공 |
| B-T21 | 확보 | 결과·Migration 계획·자원 사용보고서 보정 |
| B-T22 | 대기 | 공유 환경 승인·구성 미제공 |

## 잔여 작업 인계

| 항목 | 현재 상태 | 완료 조건 | 현재 조치 |
|---|---|---|---|
| 정식 ownership Writer·DDL | 외부 제공 대기 | production 서비스·Migration·제약·트랜잭션 계약 제공 | fixture를 production으로 승격하지 않음 |
| 거래 DDL 일반 Flyway 편입 | 선행 계약 대기 | ownership 순서와 공유 DB 이력 확정 후 fresh/upgrade 통과 | pending 경로 유지 |
| legacy 채팅방 연결 | 대상 데이터 대기 | 실제 `chat_rooms.listing_id`의 정식 판매글 참조 확인 | 자동 보정하지 않음 |
| 공유 환경 적용·smoke | 승인·구성 대기 | 승인된 환경에서 Migration·API·채팅 smoke 완료 | 로컬 검증과 구분 유지 |

## 정식 ownership 제공 요청

- `InternalTransferPendingWriter.createPending(Command)`에 연결할 production 서비스 또는 저장 경계
- `productId`, `purchaseRequestId`, `fromUserId`, `toUserId`, `INTERNAL`, `PENDING` 입력 매핑
- `ownership_transfers` Migration과 `purchase_requests` FK 적용 순서
- `purchase_request_id` 1:1 및 제품별 `PENDING` 최대 하나를 보장하는 실제 제약명
- 거래 Service와 같은 DataSource·트랜잭션 참여 및 실패 전파 계약
- 검증 대상 브랜치·PR·SHA와 제공 담당자

제공 전에는 테스트 Writer를 production으로 이동하거나 새로운 ownership 테이블·API를 만들지 않는다.
