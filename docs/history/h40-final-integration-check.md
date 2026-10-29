# H40 통합 후보 제출 전 Gate 0 확인

이 Gate 0 기록은 과거 조사로 보존한다. 현재 통합·실행 판정은 문서 마지막의 **v1.1 통합 후보 최종 검사**를 기준으로 한다.

## 판정

- H40 제출 전 검사: **BASELINE_UNCONFIRMED**. 고정 main과 H40 수용본이 함께 있는 로컬 후보를 찾지 못했다.
- 정식 거래·이전 전체 인수: **BLOCKED / CONTRACT_REVIEW**. 통합 후보에서의 실행 및 내부 이전 포트 계약 검증이 필요하다.
- 검사 지시서 Gate 0의 후보 없음 분기에 따라 자동 merge, 시험 보정, 본검사를 시작하지 않았다.

## 확인 기준과 환경

| 항목 | 확인 결과 |
|---|---|
| 확인 일시 | 2026-10-28 KST |
| 최초 작업 경로 | `C:\Workspace\AIBE7_FinalProject_Team1` |
| 최초 branch / HEAD | `feature/trade-p4-completion` / `0da56160a901f920d9f5469b0b41112b5c22dd1a` |
| H40 경로 | `C:\Workspace\AIBE7_FinalProject_Team1-h40-20261028` |
| H40 branch / HEAD | `feat/h40-history-integration-20261028` / `003058e4f764ae30bef24833bc93e4f76f768684` |
| 고정 main 및 fetch 후 양쪽 원격 main | `f5ad1025abb86a79cb8ff35d7178c8e80aade151` |
| 공통 조상 | `b21c42e96eb6745f95d8f9f764a3a99075d9c35a` |
| H40 / main 고유 커밋 수 | 13 / 18 |
| 최초 등록 worktree | 6개, 전부 clean, merge/rebase 진행 없음 |
| refs / reflog 확인 | 모든 ref 및 reflog 고유 70개 commit에서 두 기준을 모두 포함한 후보 0개 |
| T0 / T1 | 미식별 / 미생성. 이 보고서 커밋은 통합 후보가 아니다 |
| 보고 전용 branch | `test/h40-final-gate0-20261028`, H40 수용 SHA에서 분기 |
| 보고 전용 경로 | `C:\Workspace\AIBE7_FinalProject_Team1-h40-final-20261028` |
| 실행 모델 | GPT-6 기반 Codex. 정확한 런타임 모델 ID 및 reasoning effort는 세션에 노출되지 않아 확인 불가 |
| 도구 / 환경 | Windows PowerShell, Git, 공개 GitHub API 읽기 조회 |
| JDK / Gradle / PostgreSQL / Redis | Gate 0 종료로 실행 환경 선정 및 시험 실행 NOT_RUN |

origin은 `https://github.com/goarns77/AIBE7_FinalProject_Team1`, upstream은 `https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1.git`이다. fetch 후 고정 main보다 더 전진한 원격 변경은 없었다. 로컬 main은 원격 main과 달랐으며 변경하지 않았다.

## 명령과 근거

```text
git worktree list --porcelain
git status --short --branch
git branch -a --format='%(refname:short) %(objectname)'
git fetch --all
git for-each-ref --format='%(refname)'
git merge-base --is-ancestor f5ad1025abb86a79cb8ff35d7178c8e80aade151 <각 ref 또는 reflog commit>
git merge-base --is-ancestor 003058e4f764ae30bef24833bc93e4f76f768684 <각 ref 또는 reflog commit>
git reflog --all --format='%H'
git merge-base f5ad1025abb86a79cb8ff35d7178c8e80aade151 003058e4f764ae30bef24833bc93e4f76f768684
git rev-list --left-right --count 003058e4f764ae30bef24833bc93e4f76f768684...f5ad1025abb86a79cb8ff35d7178c8e80aade151
```

H40 branch에서 main ancestry 종료 코드는 1, H40 ancestry 종료 코드는 0이다. 두 원격 main에서는 각각 0, 1이다. 다른 로컬 branch는 두 기준 모두 ancestry 종료 코드 1이다. 모든 ref에 대한 종료 코드는 검수 ZIP의 Gate 0 원본 로그에 수록한다. 다른 SHA로 재구성한 후보도 확인되지 않았다. H40 작업 트리는 수용 SHA와 tree가 동일하며, 버전 갱신·recorder·AI 이벤트·R1 허용 필드·T1 오류 분리 및 완료 404 음성 대조가 남아 있는 것을 정적으로 확인했다. 이는 새 main과 통합한 동작의 검증은 아니다.

부모 저장소에서 `head=goarns77:feat/h40-history-integration-20261028`, `state=all` 공개 API 조회 결과는 빈 배열이었다. 다른 head의 PR 부재를 의미하지 않는다. gh PowerShell wrapper는 실행 정책으로 실행되지 않아 공개 API로 조회했다. 원격 쓰기는 수행하지 않았다.

## F-ID별 상태

| ID | 상태 | 연결 수준 | 근거 / 잔여 |
|---|---|---|---|
| F-01 | BLOCKED | 미실행 | 두 기준을 포함한 T0 없음. H40 수용본 자체 보존은 정적 확인 |
| F-02 | NOT_RUN | 미실행 | 후보 부재로 형식·컴파일·전체 test·build 본검사 보류 |
| F-03 | NOT_RUN | 미실행 | main ownership DDL 존재와 기존 fixture 차이는 정적으로 확인. 후보 실행 없음 |
| F-04 | NOT_RUN | 미실행 | 통합 후보의 history→P3 실행 증거 없음 |
| F-05 | NOT_RUN | 미실행 | main reader는 공급됐으나 H40 후보에 미통합 |
| F-06 | NOT_RUN | 미실행 | 수용본 R1 정적 보존. 통합 후보의 공개 데이터 보호 검사 미실행 |
| F-07 | NOT_RUN | 미실행 | 수용본 이벤트 호출 정적 보존. 실제 listener·접수 회귀 미실행 |
| F-08 | NOT_RUN | 미실행 | 새 main과 조합된 변경 확인·P4 회귀 미실행 |
| F-09 | NOT_RUN | 미실행 | 수용본 T1 정적 보존. 통합 후보 롤백·경합 미실행 |
| F-10 | NOT_RUN | 미실행 | 기능 플래그 변경 없음. 통합 후보 설정 공존 미실행 |
| F-11 | NOT_RUN | 미실행 | P2 PostgreSQL / HTTP / STOMP 미실행 |
| F-12 | CONTRACT_REVIEW | 미실행 | 정식 ownership 및 거래 내부 포트 대응은 통합 후보에서 검증 필요 |

이번 시험 실행 횟수 0회다. tests/passed/failures/errors/skipped 집계는 N/A이며 SKIPPED로 세지 않는다. 이전 실행의 성공 수치나 XML을 이번 후보의 증거로 사용하지 않았다. 신규 JUnit XML·Gradle 실행 로그는 생성되지 않았다. 과거 자료는 기존 worktree의 build 디렉터리에 그대로 보존하며 이번 실행 증거 ZIP에 혼합하지 않는다.

## 필요한 통합 경로 및 계약 확인

별도 통합 승인을 받은 뒤 H40 수용 SHA에서 새 통합 worktree/branch를 만들고 고정 main `f5ad102`를 통합하여 T0를 제공해야 한다. 이미 다른 경로에 후보가 있다면 해당 경로·전체 SHA 및 변경 출처를 제공하면 재병합 없이 검사할 수 있다. 이후 T0 수정 전 검사, fixture 정합화, T1 고정, 최종 기본 검사와 선택 회귀 순으로 진행한다. 이번에는 해당 merge를 수행하지 않았다.

main의 실제 `ownership_transfers`는 `token_hash`, `expires_at` 필수값과 PENDING/COMPLETED/CANCELLED/EXPIRED 상태 제약을 갖는다. 기존 H40 시험 대역의 중복 CREATE와 ACCEPTED 상태 사용을 단순 IF NOT EXISTS나 assertion 치환으로 숨겨서는 안 된다. 요구사항 OWN-01/02의 ACCEPTED와 현행 COMPLETED 사이의 의미·API·DB 변환 근거를 확인하고 내부 pending/cancellation/completion 포트의 제공·잠금·트랜잭션·부수효과 대응을 별도로 검토해야 한다. 이 차이는 아직 실행으로 재현한 실패가 아니다.

H40-06, ownership 정식 거래 연결, R07, P5, 공유 DB 이행과 운영 활성화는 계속 이월한다. 이번에 운영·시험 코드·Migration·설정·기능 기본값은 수정하지 않았다. 로컬 PostgreSQL·Redis·앱 서버는 생성하지 않았으며 사용자 자원을 정리할 필요도 없었다.

## 제출 자료와 PR 초안

보고 전용 로컬 커밋과 `review-h40-final-<보고커밋짧은SHA>.zip`을 제출한다. ZIP에는 이번 Gate 0 로그·SHA-256 manifest·보고서·변경 diff·변경 문서 전체를 포함한다. main 대비 diff는 통합 후보가 아닌 보고 branch와 고정 main의 비교임을 명시한다. T0 이후 diff는 T0 부재로 제공할 수 없다. 신규 시험 로그/XML은 원본 실행 증거 미첨부(미실행)로 기록한다.

PR은 생성하지 않는다. 향후 T0 확인과 검사 후 사용할 제목 초안은 `fix(trade): H40 history 연결 및 최신 main 통합 검증`이다. 본문에는 H40 수용 SHA와 통합 main SHA, 최종 검사 HEAD, 실제 실행 결과, ownership 내부 포트 및 H40-06 잔여를 기록해야 한다. 현재 상태는 BASELINE_UNCONFIRMED이므로 PR 제출 근거로 사용할 수 없다.

## v1.1 통합 후보 최종 검사

### 최종 판정

- **H40 제출 전 검사: CHANGES_REQUIRED.** 고정 main과 H40의 로컬 통합 및 직접 영향 회귀는 완료했다. 기본 전체 시험의 main/demo Flyway 버전 충돌 3건이 남아 있어 PR 제출·병합 준비 완료로 판정하지 않는다.
- **정식 거래·이전 전체 인수: BLOCKED / CONTRACT_REVIEW.** ownership 모듈과 기본 pending writer는 공급됐지만 취소·완료 내부 포트, 상태 매핑, 필수 부수효과의 정식 접합은 미완료다.
- R1·T1 종료 판정을 유지한다. 새로운 공개 정책·정식 ownership 어댑터·H40-06·P5를 구현하지 않았다.

### 후보 및 의미 보존

| 항목 | 값 |
|---|---|
| 작업 경로 | `C:\Workspace\AIBE7_FinalProject_Team1-h40-final-20261028` |
| 작업 branch | `test/h40-final-gate0-20261028` |
| 시작 HEAD | `44bd3f28c6208da15a6c88ae8d2fe46320a14fa7` (clean) |
| 수용 H40 | `003058e4f764ae30bef24833bc93e4f76f768684` |
| 고정 main / 종료 전 원격 확인 | `f5ad1025abb86a79cb8ff35d7178c8e80aade151` / 동일 |
| T0 merge | `22489cac15b1539a3331c8569e03f58a8195f5f7` |
| T1 코드·시험 | `50c9632948bf3ea9ec0b59eb1a36d4a08bac0563` |
| 실행 모델·effort | 세션 설명은 GPT-6 기반 Codex. 정확한 런타임 모델 ID·effort는 미확인 |
| 환경 | Windows PowerShell, Temurin 17.0.19+10, Gradle Wrapper 9.7.1, PostgreSQL 17.11, Redis 8.10.3 |

`git merge --no-ff --no-commit f5ad102...`는 충돌 없이 완료됐고 양쪽 diff를 확인해 T0를 커밋했다. main과 수용 H40에 대한 T0 ancestry 종료 코드는 모두 0이다. 공통 조상 이후 각 측에서 변경한 모든 경로를 T0와 비교한 결과 해당 측 원본과 다른 파일은 0개다(`preservation.log`). 자동 merge 자체의 파일 삭제·덮어쓰기 해결은 없었다.

최종 `HistoryService`는 수용본과 byte-level Git tree 비교가 동일하다. 일반/사양 버전 증가, P3 recorder, AI 이벤트, R1 앞뒤 사양 허용 필드 제한은 그대로다. T1의 P4 worker별 오류 정책과 완료 404 음성 대조도 유지했다. 수용본의 history/P2/P3/P4 시험 메소드 각각 13/15/17/12개는 삭제 0개다(`final-preservation.log`). 일반·pending·demo Migration 및 설정은 고정 main과 동일하며 이번에 변경하지 않았다.

### 수정 전 실패와 최소 호환성 보완

1. T0에서 `spotlessCheck classes testClasses test build --no-daemon`를 실행했다. 형식 검사는 통과했으나 main의 `ListingDefaultConfig`가 H40의 `findReservedByProductIdForUpdate`를 구현하지 않아 compileJava가 실패했다. 시험 집계는 N/A이며 이후 task를 실행했다고 보지 않는다(`t0-basic.log`).
2. 허용된 merge 호출부 호환성 보정으로 해당 메소드만 기존 `JdbcPublishedListingAccess`에 위임했다. 기존 default 메소드·업무 정책을 바꾸지 않았고 새로운 ownership 포트를 구현하지 않았다. `spotlessApply testClasses`가 통과했다(`compat-compile.log`).
3. fixture 수정 전 선택 실행은 T0 + 위 단일 compile 보정 상태였다. reader 18건 PASS, P2 HTTP 7건과 P2/P3/P4 초기화 오류 3건 FAIL로 XML 집계 28건 중 18 PASS / 10 failures였다. repository의 initializationError 1건은 해당 클래스의 모든 시험이 실행됐다는 의미가 아니다. 원인은 실제 일반 Migration이 만든 `ownership_transfers`를 옛 fixture가 다시 CREATE하는 것이다(`t0-plus-compile-compat-selected.log`, 해당 XML).
4. 미합의 계약을 실제 스키마에 맞춘다는 이유로 토큰 NULL assertion이나 ACCEPTED 상태를 치환하지 않았다. P2 repository/HTTP, P3, P4의 시험 대역만 `trade_fixture_transfers`라는 별도 테이블로 격리했다. 기존 제약·assertion·원자성·토큰 NULL·ACCEPTED 기대 의미는 보존했다. 정식 `ownership_transfers`는 삭제·교체·완화하지 않았다. 이 검사는 **DB_PARTIAL**이며 정식 ownership 스키마와 정합한 내부 포트 인수 시험으로 간주하지 않는다.
5. 별도의 `OwnershipSchemaPostgresIntegrationTest`는 정식 일반 Migration 그대로 토큰·만료 NOT NULL, 제품별 PENDING slot 유일성·해제, ACCEPTED 거부 / COMPLETED 허용을 3건으로 검사한다. 이 상태 제약 검사는 ACCEPTED→COMPLETED 업무 매핑 승인과 다르다.
6. 기존 P3 history 연결 시험에 실제 `JdbcListingHistoryReader`의 신규 이력 조회·미검토 자유입력 비노출 검증을 추가했다. 기존 `ListingSummaryHttpTest`에 실제 `HistoryService`에서 실제 R10 listener/store/repository로 이어지는 커밋·롤백, 요약 큐 저장 실패가 이미 커밋된 history에 영향을 주지 않는 시험 2건을 추가했다. 이 AI 시험의 공개 reader와 전송 계층은 기존 대역이고 실제 외부 모델은 호출하지 않는다. 실제 reader/공개 HTTP는 별도의 실제 구현 시험으로 검증한다.
7. P2 HTTP 시험 서버는 `server.address=127.0.0.1`로 제한했다. 이상의 코드·시험을 T1로 고정한 뒤 dirty diff 없이 최종 실행했다.

### 실행 명령과 결과

실행 스크립트 `run-check.ps1`과 실행별 JSON에 전체 인수·시각·HEAD·종료 코드를 수록한다. 모든 실행은 wrapper `.bat`와 위 JDK 17을 사용했다. 기본 검사는 opt-in/SALE_DRAFT 환경변수를 제거하고 거래 기능 기본 OFF를 유지했다. 외부 키는 자식 셸에서 제거하고 AI 기본 실행을 비활성화했으며, AI 시험이 명시적으로 ON한 경우에도 기존 전송 대역을 사용했다.

```text
T0: gradlew.bat spotlessCheck classes testClasses test build --no-daemon
compile 호환성: gradlew.bat spotlessApply testClasses --no-daemon
T1 기본: gradlew.bat spotlessCheck classes testClasses test build --rerun-tasks --no-daemon
T1 선택: gradlew.bat test --tests '*PurchaseRequestPostgresIntegrationTest' --tests '*PurchaseRequestHttpPostgresTest' --tests '*TradeP3PostgresIntegrationTest' --tests '*TradeP4PostgresIntegrationTest' --tests '*JdbcListingHistoryReaderTest' --tests '*ListingHistoryIntegrationHttpTest' --tests '*OwnershipSchemaPostgresIntegrationTest' --tests '*ListingSummaryHttpTest' --rerun-tasks --no-daemon
git diff --check
```

선택 시험은 P2/P3/P4의 소스에 정의된 `P{2,3,4}_TRADE_TEST_ENABLED=true`, `P{2,3,4}_TRADE_TEST_DB_URL/USER/PASSWORD`를 사용했다. DB URL은 `jdbc:postgresql://127.0.0.1:55436/sale_ai_test`, 사용자는 합성 전용 계정이다. DB는 루프백 trust 설정이며 script의 password 인수는 인증 비밀이 아닌 합성 placeholder다. P2 Redis는 `P2_TRADE_TEST_REDIS_HOST=127.0.0.1`, `PORT=56386`, `FLUSH_ALLOWED=true`를 이번 전용 인스턴스에만 적용했다. reader·AI 시험은 소스의 `SALE_DRAFT_TEST_DB_URL/USER/PASSWORD`를 같은 전용 DB에 연결했다. 일반 자격 증명이나 .env를 읽거나 보고하지 않았다.

| 실행 | tests | PASS | failures | errors | SKIPPED | 판정 |
|---|---:|---:|---:|---:|---:|---|
| T0 기본 | N/A | N/A | N/A | N/A | N/A | compileJava FAIL |
| T0 + compile 보정 선택 | 28 | 18 | 10 | 0 | 0 | fixture 초기화 FAIL |
| T1 기본 전체 | 757 | 699 | 3 | 0 | 55 | test/build FAIL |
| T1 선택 전체 | 109 | 109 | 0 | 0 | 0 | PASS |

T1의 spotlessCheck, classes, testClasses, bootJar, jar task는 성공했다. 전체 test의 실패 때문에 build는 성공이 아니다. 포맷·컴파일 성공을 전체 build 성공으로 확장하지 않는다. 새 Migration 실패를 숨기려고 기대값·locations·적용 이력 또는 SQL을 바꾸지 않았고 변경 없는 시험을 불필요하게 반복하지 않았다.

| T1 PostgreSQL 선택 묶음 | tests / PASS | 연결 수준 |
|---|---|---|
| P2 repository | 15 / 15 | DB_PARTIAL: 이전 포트는 별도 대역 테이블 |
| P2 HTTP/STOMP | 7 / 7 | HTTP: 인증·쿠키·CSRF·예약·채팅, 이전 포트 대역 |
| P3 | 17 / 17 | DB_PARTIAL: 실제 history/P3/reader, ownership 취소 대역 |
| P4 | 12 / 12 | DB_PARTIAL: 실제 거래/history, ownership 완료·공개 상태·증빙·초안 및 공개 상세 일부 대역 |
| JdbcListingHistoryReader | 10 / 10 | REAL_MODULE(SQL reader): 읽기 최소 원천 fixture |
| ListingHistoryIntegrationHttp | 8 / 8 | HTTP: 정식 Migration·history API·reader·미리보기·상세·AI 입력 projection |
| OwnershipSchemaPostgresIntegration | 3 / 3 | REAL_MODULE(DB schema): 정식 Migration, 내부 포트 미검증 |
| ListingSummaryHttp | 37 / 37 | HTTP / REAL_MODULE(history→R10 저장 접수): reader·AI 전송·실행 제한 일부 대역 |

기본 SKIPPED 55건 중 PostgreSQL opt-in 54건(P2 22 + P3 17 + P4 12 + ownership schema 3)은 선택 실행의 동일 classname/testcase 식별자로 모두 PASS 대응됐다. Redis 단위시험 1건은 기본 SKIPPED이고 별도 실행은 NOT_RUN이다. 대응 CSV는 `skipped-to-selected.csv`다. 선택 실행에는 기본에서 이미 실행된 reader/AI 시험도 있으므로 두 실행의 건수를 단순 합산하지 않는다. P3-C01~C05(KEEP/CANCEL 포함)와 P4-DB02-B 2건은 T1 전용 PostgreSQL에서 다시 PASS했고 이전 부분 연결 의미를 유지했다.

### 남은 실패: main/demo Migration 충돌

실행 기대는 기존 demo 적용 이력과 요약을 유지하며 업그레이드되는 것이다. 실제 결과는 Flyway resolver 단계에서 `Found more than one migration with version 2026102702`로 중단되는 것이다.

| 경로 | 같은 버전의 파일 |
|---|---|
| 일반 | `db/migration/V2026102702__create_ownership_tables.sql` |
| demo | `db/demo/sale-draft/V2026102702__version_listing_card_summary.sql` |

일반 locations 단독 및 일반+pending 조합에는 이번 버전 중복이 없다. 반면 `application-sale-draft-demo.yaml`은 실제로 일반+demo 두 경로를 함께 사용한다. 따라서 향후 충돌 가능성만 기록한 것이 아니라 이 조합에서 재현한 실제 실패다. 일반 운영 프로필에 이 버전 충돌이 있다고 확대하지 않는다.

실패 testcase는 `ListingCardSummaryMigrationTest.upgradesTheAlreadyAppliedDemoVersionWithoutLosingSummaryOrChecksumHistory`, `ListingDemoUpgradeTest.upgradesPersistedDemoAndPublishesWithAnotherConnectionWithoutLosingDraft`, `ListingDemoUpgradeTest.restoresOnlyMissingDemoSellerAndPreservesExistingAccount`이다. 재현은 위 기본 명령 또는 이 두 클래스 선택 실행이며, 원본 XML/로그에 양쪽 offending 파일이 있다. 실패한 test와 두 SQL, 관련 설정은 모두 고정 main과 동일하다. 별도 main checkout에서 재실행했다고 주장하지 않는다.

최소 수정 방향은 담당자와 일반·demo 각 DB의 실제 적용 이력을 먼저 확인하고, 이미 적용된 SQL/버전/checksum 보존과 업그레이드 호환성을 함께 충족하는 버전 재배치 계획을 확정하는 것이다. 신규 일반 ownership version과 기존 demo version의 공존 문제는 실행 전에 resolver에서 발생하므로 단순 후속 SQL 추가만으로 없어지지 않는다. 이번 범위에서 임의 Migration 재명명·본문 변경·Flyway repair/clean·locations 우회·시험 assertion 약화를 수행하지 않았다. 관련 수정 합의 후 실패 묶음과 기본 test/build를 재검증해야 한다.

### F-01~12 판정

| ID | 상태 | 연결 수준 | 이번 근거 |
|---|---|---|---|
| F-01 | PASS | 정적 의미 보존 | T0 양쪽 ancestry 0, 양쪽 파일 비교 mismatch 0, R1/T1 및 수용 시험 보존 |
| F-02 | FAIL | UNIT / HTTP / DB_PARTIAL | 형식·컴파일·jar 성공, 기본 전체 test 3건 실패로 build 실패 |
| F-03 | FAIL | REAL_MODULE(DB schema) / DB_PARTIAL | 일반/pending 실제 적용과 독립 schema 3건 PASS, 일반+demo 중복 버전 FAIL |
| F-04 | PASS | DB_PARTIAL | 실제 history→버전→P3 사본→양측 확인 무효화와 동일 트랜잭션 롤백 |
| F-05 | PASS | REAL_MODULE / HTTP | 실제 reader 10 + history/publication/detail HTTP 8, P3 신규 이력 실제 reader 조회 |
| F-06 | PASS | UNIT / REAL_MODULE / HTTP | R1 신규·기존 사양 문자열 비노출, 소유자 원문 보존, 공개 metadata와 검토 상태 |
| F-07 | PASS | REAL_MODULE(history→R10) | 실제 서비스 커밋→실제 저장 큐, 롤백 미접수, 저장 접수 실패 후 history 유지. AI 전송 대역 |
| F-08 | PASS | DB_PARTIAL | 미확인 차단·KEEP·최신 확인·과거 버전 거부·양측 확인, completion 대역 명시 |
| F-09 | PASS | DB_PARTIAL | 기존 P3/P4 롤백·경합, worker별 오류 및 완료 404 음성 대조 |
| F-10 | PASS | HTTP / DB_PARTIAL | 기본 OFF 공존, 실제 reader ON 선택, 종료 판매글 상세·요약 보호. P3/P4 자동 구성 전체 ON은 정식 포트 대기 |
| F-11 | PASS | HTTP / DB_PARTIAL | 실제 P2 PostgreSQL·Redis·쿠키·CSRF·권한·HTTP/STOMP 22건 |
| F-12 | CONTRACT_REVIEW | 정적 계약 / 실제 DB 제약 | 아래 계약표. 정식 이전 전체 인수는 BLOCKED |

### ownership 제공 계약과 이월

| 항목 | 현재 공급 | 거래 포트와 차이 / 후속 |
|---|---|---|
| pending | `ListingDefaultConfig.defaultInternalTransferPendingWriter`, `@Profile("!test")`, missing-bean 조건 | 같은 JDBC 호출 트랜잭션에서 INTERNAL/PENDING·pending_slot=1·합성 token_hash·24시간 만료를 저장. 포트 Javadoc의 미공급 설명은 현재 main 공급 상태와 다름. 구매 요청/수신자 관계·요청당 유일성 및 토큰 정책의 정식 인수 필요 |
| 취소 | `OwnershipTransferService.cancelTransfer` 존재 | 이전 ID·발신자·PENDING 확인 후 취소/감사. `InternalTransferCancellationPort` Bean은 미공급. 거래 command의 제품/요청/발신·수신 조건부 갱신 계약과 별도 접합 필요 |
| 완료 | `OwnershipTransferService.acceptTransfer` 존재 | 토큰 hash로 이전 행 잠금, 수신자·만료·현재 소유자 검사, 소유자 갱신·상태 COMPLETED·감사. `InternalTradeCompletionPort` Bean 미공급 |
| 상태 | 요구사항 OWN-01/02의 ACCEPTED / 실제 enum·DDL의 COMPLETED | API `AcceptTransferResponse`는 transferId/productId/newOwnerId/completedAt을 반환하며 상태 매핑을 선언하지 않음. 동등성 합의는 확인되지 않아 CONTRACT_REVIEW |
| ID·토큰·만료 | 실제 이전은 독립 transferId·token_hash·expires_at, purchase_request_id는 별도 열 | 거래 command의 requestId·수신자와 명확히 대응해야 함. DIRECT 토큰 API를 성공 고정 내부 포트로 대체하지 않음 |
| 잠금·트랜잭션 | ownership `@Transactional`, JPA 이전 행 잠금과 JDBC 제품/감사; 기본 단일 DB 구성 | 거래의 제품→판매글→요청→이전 순서와 달리 토큰 이전 행부터 잠금. 동일 트랜잭션 매니저 참여 및 완료/이전 경합 정식 인수는 미실행 |
| 필수 부수효과 | actual accept는 소유자·완료 상태·감사 저장 | 거래 완료의 시스템 TRANSFER history·누적 이력 PRIVATE·증빙 보호·초안 처리·거래/PENDING 종료를 한 트랜잭션으로 제공하는 정식 포트 미연결 |

P5에서는 실제 ownership entity/repository의 토큰·만료·pending-slot과 잠금 API, 제품 소유자 갱신 및 감사 저장을 재사용할 수 있다. 상태·거래 ID 매핑, 잠금 순서, 완료 원자성 부수효과는 담당 합의와 보강이 필요하다. P5나 신규 어댑터는 이번에 구현하지 않았다. 기본 거래 플래그가 OFF인 구성과 독립 history/reader 경로는 정상 실행됐으며 정식 포트가 없는 전체 거래 ON 구성의 완료를 주장하지 않는다.

H40-06 changeId/정정·무효 새 review, R07 사유 정책, 보존/공유 DB 이행 이력, 화면 인수, 운영 활성화·배포는 계속 이월한다. reader는 main에서 공급되어 실제 검사했으므로 더 이상 일괄 미제공으로 분류하지 않는다.

### 자원·증거 및 제출

2026-10-28 18:55 KST에 승인된 Rancher Desktop을 hidden 기동했다. 18:56:59에 새 `h40-v11-postgres-20261028`(루프백 55436, tmpfs)과 `h40-v11-redis-20261028`(루프백 56386, 비영속)을 만들었다. 각 시험은 고유 격리 schema에서만 Migration·TRUNCATE·DROP을 수행했고 P2 Redis FLUSHDB도 이 전용 자원에만 실행했다. 19:06:21 이후 두 컨테이너를 정상 종료·자동 제거하고 잔여 0, 두 포트 LISTEN 0을 확인했다(`resources.log`). 엔진과 다른 사용자 컨테이너는 종료하지 않았다. host docker proxy 지연 시 `rdctl shell docker`로 해당 자원만 관리했다.

과거 증거는 실행 전에 `C:\Workspace\h40-v11-evidence-20261028\prior-not-current`로 보존했다. 새 검수 ZIP에는 과거 실행을 섞지 않고 이번 t0/t1 로그·XML·실행 JSON·testcase 대응 CSV·보존 확인·자원 기록·main 대비 누적 diff·T0 이후 diff·변경 파일 전체와 SHA-256 manifest를 넣는다. ZIP용 로그/XML에 필요한 비밀값 제거가 있으면 manifest에 파일·종류·횟수를 기록하고 원본은 검수 디렉터리에 보존한다. 전체 build/.git/.env/실제 자격 증명/DB dump는 넣지 않는다.

동일 H40 원격 head의 부모 저장소 open PR 조회 결과는 빈 배열이었다. 다른 head 전체의 PR 부재를 주장하지 않는다. 이번 T0/T1/보고 커밋은 로컬에만 남기며 push·PR 생성/갱신·병합·배포를 수행하지 않았다.

PR 초안 제목은 `fix(trade): H40 history 연결 및 고정 main 통합 검증`이다. 본문 초안: 수용 H40 003058e와 고정 main f5ad102를 T0 22489ca로 통합하고 T1 50c9632에서 호출부 호환성·독립 fixture·실제 연결 시험을 보완했다. 직접 영향 PostgreSQL/Redis 109건 PASS, 기본 전체 757건 중 699 PASS/3 FAIL/55 SKIPPED이며 전체 build는 실패했다. 실패 원인은 일반/demo V2026102702 충돌로 제출 전 해소가 필요하다. ownership 취소/완료 정식 포트 및 상태 계약·H40-06은 별도 잔여이며 기능 기본 OFF·pending 격리를 유지한다. 현재 판정은 CHANGES_REQUIRED이고 PR 제출·병합 승인 상태가 아니다.
