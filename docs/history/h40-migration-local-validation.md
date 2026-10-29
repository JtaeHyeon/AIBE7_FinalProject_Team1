# H40 로컬 Migration 보정·회귀 결과

## 현재 기준 (v1.3)

일반 ownership `2026102702`와 일반 카드 요약 `2026102802`를 기준으로 한다. `2702.1` 개명을 철회하고 충돌 demo 카드 요약 SQL은 자료 경로로 퇴역시켰다. 현재 실행 결과와 지원 범위는 [일반 기준 복구 결과](h40-general-authority-result.md)를 따른다.

**이하 전체는 이전 C0/v1.2 실행의 역사 기록이다.** `CURRENT_BASELINE_ADOPTED`, 일반 DB `UPGRADE_INCOMPATIBLE`, 옛 demo 자동 업그레이드 지원은 당시 판정이며 v1.3 현재 계약이 아니다. 기존 로그·수치는 이번 실행의 통과 근거로 재사용하지 않는다.

## 과거 2026-10-29 개발 기준·지원 범위 결정 (v1.2)

사용자 결정에 따라 R0 `863907f546549382223bf4e73ccdd832e27b8f05`의 ownership 구현과 보정된 `V2026102702_1__create_ownership_tables.sql`을 후속 개발 기준으로 채택한다. 현재 판정은 **LOCAL_VALIDATED / CURRENT_BASELINE_ADOPTED**다. 아래의 `PENDING_TEAM_DB_DECISION` 및 병합 전 적용 이력 확인 요구는 당시 판단으로 보존하며, 이번 지원 범위 결정으로 대체한다. 팀 전체 합의나 실제 DB 이행 완료를 뜻하지 않는다.

| 경로 | 현재 지원 범위 |
|---|---|
| 현재 후보로 구성하는 신규 일반 DB | 현행 개발·검증 기준 |
| 검증한 일반+pending 순차 적용, 신규 일반+demo | 기존 검증 범위 유지. pending 일반 편입·운영 활성화 아님 |
| 검증한 구버전 demo 출발 상태 | 기존 데이터·이력 보존 검증 유지. 모든 과거 상태 호환 보증 아님 |
| 옛 일반 ownership `V2026102702` 적용 DB | **자동 업그레이드 미지원 / 구체적 요청 시 별도 이행 작업**. 기존 HARNESS_PASS / UPGRADE_INCOMPATIBLE 유지 |
| 실제 팀 보존·공유·운영 DB | NOT_TESTED. 이번 작업에서 접속·변경·이행하지 않으며 초기화·폐기를 추정하지 않음 |

구버전 이력 미확인만으로 일반 PR 제출을 중단하거나 Draft를 강제하지 않는다. 지원 대상의 실제 회귀·충돌, CI와 리뷰는 별도로 판단한다. 정식 ownership 전체 연결 및 H40-06/R07/P5는 계속 별도 이월이며 기존 BLOCKED / CONTRACT_REVIEW를 해제한 것이 아니다. 구버전 지원 요청 시 실제 locations와 version/script/checksum/success를 분류하고 별도 보존·이행안을 검토한다. 데이터 삭제·reset·repair·이력 직접 수정은 이번 작업에 포함하지 않는다.

### 기존 검증 재사용 근거

- 시작 HEAD와 원격 제출 브랜치는 R0, 팀 main은 B `f5ad1025abb86a79cb8ff35d7178c8e80aade151`로 동일했다. R0 이후 변경은 없었다.
- `git diff --name-status 7b701c017c3711b7003bd58735c8e00994ee5184 HEAD`는 결과·인계 문서 두 파일만 표시했다. C0와 R0의 `src` tree는 모두 `fd397a9c162e9da70771ef62dd145b05aa178ad1`이며 `.github`, Gradle 설정·wrapper·scripts도 동일했다. 이번 후속 변경 역시 문서만 포함한다.
- 기존 검수 디렉터리의 MANIFEST와 실행별 JSON·로그·JUnit XML을 확보했다. MANIFEST의 145개 파일 SHA-256을 전부 대조해 불일치 0건이며 네 실행의 XML 집계가 보고와 일치했다. 기본 759건 중 704 PASS / 55 SKIPPED, 실패·오류 0 및 build 성공, 선택 PG·Redis 109/109 PASS는 **C0의 기존 실행**이다. 기본 skip 54건의 선택 실행 대응 및 Redis 단위시험 1건 별도 NOT_RUN을 유지한다.
- v1.2 작업에서는 문서 diff·링크·지원 범위·실행 트리 동일성을 검사하며 spotlessCheck/test/build 및 전용 DB 회귀는 재실행하지 않는다. 정확한 제출 SHA·PR/CI 조회 결과와 증거 대조 수준은 이번 검수 자료의 MANIFEST/PR_INFO에 기록한다. 과거 원본 로그를 새 실행으로 복제하지 않는다.

## 과거 C0 판정

**LOCAL_VALIDATED / PENDING_TEAM_DB_DECISION.** 신규 DB와 기존 demo 업그레이드는 H2·전용 PostgreSQL에서 검증했다. **옛 일반 ownership `2026102702`가 적용된 DB는 UPGRADE_INCOMPATIBLE**이며 실제 팀 환경의 지원 여부·채택 판단은 병합 전에 확인해야 한다. 이 조건을 정상 업그레이드로 표현하지 않는다. 정식 거래·이전 전체 인수는 계속 **BLOCKED / CONTRACT_REVIEW**다.

이 결과는 `28ad18f` 조사 보고의 후속이다. v1.1 지시로 적용 이력 미확인과 로컬 가정 검증을 분리해 실제 수정·실행했다. 과거 조사·실패 결과 문서는 역사 자료로 보존하며 이 문서와 [팀원 인계](h40-migration-team-handoff.md)를 현재 결과로 사용한다.

| 기준 | 값 |
|---|---|
| worktree / branch | `C:\Workspace\AIBE7_FinalProject_Team1-h40-final-20261028` / `test/h40-final-gate0-20261028` |
| S | `28ad18f0a37eb3cd7756bfa5db8bbca2331a14eb` |
| C (이번 코드·시험 실행본) | `7b701c017c3711b7003bd58735c8e00994ee5184` |
| 고정 B / U_start | `f5ad1025abb86a79cb8ff35d7178c8e80aade151` / 시작 조회에서 동일 |
| 이전 실행 T1 / 수용 H40 | `50c9632948bf3ea9ec0b59eb1a36d4a08bac0563` / `003058e4f764ae30bef24833bc93e4f76f768684` |
| R / U_pr / 실제 원격 결과 | R은 이 결과·인계 문서만 추가한 제출 head. 전체 SHA와 제출 직전 U_pr, 실제 PR/CI 결과는 검수 ZIP의 MANIFEST/PR_INFO 및 실제 PR에서 확인 |
| 실행 환경 | Windows PowerShell, Temurin 17.0.19+10, Gradle 9.7.1, PostgreSQL 17.11, Redis 8.10.3 |
| 모델·effort | 세션은 GPT-6 기반 Codex로 설명됨. 정확한 런타임 ID·reasoning effort는 미노출 |

## 보정과 보존

- 일반 ownership을 `V2026102702__create_ownership_tables.sql`에서 `V2026102702_1__create_ownership_tables.sql`로 개명했다. Flyway는 새 버전을 `2026102702.1`로 기록하며 demo `2026102702` 뒤, 후속 ownership `2026102707` 앞에 적용한다.
- 개명 전/후 및 역사 재현 fixture SQL의 Git blob은 모두 `bd1a6de9dbde5acae7bd2db5670089d5187ac383`으로 동일하다. 본문·최종 스키마·토큰/만료·상태·FK·pending-slot 제약을 변경하지 않았다.
- 기존 demo SQL의 version/script/본문, `V2026102706/2707/2801/2802`, pending 경로와 모든 기능 기본값은 유지했다. SQL 이동에 따라 현재 운영 경로에 옛 이름이 남아 있지 않은지 확인했다.
- `ListingCardSummaryMigrationTest`의 legacy target `2026102702`는 유지했다. 이 시점에 ownership 테이블이 없음을 검사하고, 당시 존재하는 컬럼만으로 기존 요약을 준비한다. 이후 `2706`의 시간 컬럼 backfill과 원래의 요약·제목·제약 assertion, demo version/script/checksum/success 동등성 검증을 수행한다.
- `H40MigrationCompatibilityTest`는 신규 일반+demo 적용 및 옛 일반 적용 DB의 validate/migrate 비호환을 검증한다. 옛 SQL fixture는 고정 B의 원문이고, 역사 준비 단계만 임시 filesystem 경로를 사용한다. 후보 적용은 실제 `classpath:db/migration` 또는 일반+demo locations를 사용한다.
- 역사 일반 경로의 나머지 SQL/Java는 B와 동일한 후보 원문이다. 후보의 새 ownership만 역사 준비에서 제외하고 B의 옛 파일을 넣어 B 일반 경로를 재구성한다. 현재 후보 경로에서 파일을 제외하거나 schema history를 직접 수정하지 않는다.
- H40 운영 Java, R1 사양 허용 필드, worker별 경합 오류 분리, 기존 JDBC 위임과 P2/P3/P4 `trade_fixture_transfers`·assertion은 S와 동일하다. 새 운영 Service/어댑터·정책은 추가하지 않았다.

## 실제 실행

C를 clean 상태로 커밋한 뒤 다음 실행을 수행했다. 각 실행은 `--rerun-tasks`를 사용했고 XML·명령·전체 C·시각·종료 코드를 build 밖 실행 ID별 디렉터리에 보존했다. 첫 집중 실행의 Gradle clean으로 옛 이름의 생성 리소스를 제거했다.

| 실행 ID | DB / 내용 | tests | PASS | failures | errors | SKIPPED | 종료 |
|---|---|---:|---:|---:|---:|---:|---:|
| c-focused-h2 | H2, 기존 두 실패 클래스 전체 + 신규 호환성 2건 | 6 | 6 | 0 | 0 | 0 | 0 |
| c-basic | 기본 H2/단위/HTTP, 형식·컴파일·전체 test·build | 759 | 704 | 0 | 0 | 55 | 0 |
| c-compat-postgres | PG, 카드 요약 2건 + 신규 호환성 2건 | 4 | 4 | 0 | 0 | 0 | 0 |
| c-selected-postgres | PG·Redis, 기존 8개 영향 회귀 묶음 | 109 | 109 | 0 | 0 | 0 | 0 | 0 |

기존 3개 실패 testcase는 C에서 통과했다. c-basic은 spotlessCheck/classes/testClasses/test/bootJar/jar/check/build가 실제 성공했다. 과거 757/109 결과를 이번 결과로 복사하거나 서로 중복된 실행 건수를 합산하지 않았다.

기본 SKIPPED 55건 중 P2 22 + P3 17 + P4 12 + ownership schema 3 = 54건은 동일 classname/testcase의 PG 선택 PASS와 대응한다. Redis 단위시험 1건은 기본 SKIPPED, 별도 실행 NOT_RUN이다. 기존 조건이나 관련 계약을 바꾸지 않았으며 이를 성공으로 세지 않는다. testcase 대응은 검수 ZIP의 `skipped-to-selected.csv`에 있다.

| PG 영향 묶음 | PASS | 연결 수준 |
|---|---:|---|
| PurchaseRequestPostgresIntegrationTest | 15 | DB_PARTIAL, 이전 준비 대역 |
| PurchaseRequestHttpPostgresTest | 7 | HTTP·실제 Redis/STOMP, 이전 준비 대역 |
| TradeP3PostgresIntegrationTest | 17 | DB_PARTIAL, 실제 history/P3/reader와 ownership 취소 대역 |
| TradeP4PostgresIntegrationTest | 12 | DB_PARTIAL, 실제 거래/history와 완료·공개 상태·증빙·초안·상세 일부 대역 |
| JdbcListingHistoryReaderTest | 10 | REAL_MODULE(SQL reader), 최소 읽기 원천 fixture |
| ListingHistoryIntegrationHttpTest | 8 | HTTP, 실제 history API·reader·공개 projection |
| OwnershipSchemaPostgresIntegrationTest | 3 | REAL_MODULE(DB schema), 정식 내부 포트 인수 아님 |
| ListingSummaryHttpTest | 37 | HTTP / 실제 history→R10 저장 접수, reader·AI 전송·실행 제한 일부 대역 |

## 가정별 호환성과 이력 근거

모두 이번 폐기 가능한 DB에 준비한 합성 상태다. 실제 팀 DB의 미적용/적용을 확인한 결과가 아니다.

| 경로 | 준비·실제 적용 | 결과 |
|---|---|---|
| 신규 일반 | 실제 일반 Migration. PG 카드 요약 일반 경로 및 ownership schema 시험 | VERIFIED |
| 신규 일반+pending | 기존 P2/P3/P4의 실제 일반 적용 후 P2, P3 pending을 순차 추가. outOfOrder는 기존 시험 전용 | VERIFIED |
| 신규 일반+demo | 실제 일반+demo 전부를 빈 schema에 적용, 카드 컬럼·ownership 필수 컬럼과 두 version/script 확인 | VERIFIED, H2·PG |
| 구버전 demo | 실제 legacy target `2026102702`, ownership 없음, 기존 요약 준비 후 후보로 migrate. demo 이력과 backfill·요약 보존 | VERIFIED, H2·PG |
| B의 옛 일반 ownership 적용 | B 일반 SQL/Java를 정상 migrate하여 `2702` ownership 및 `2707`·`2802`까지 준비. 실제 제약에 맞는 사용자/제품/PENDING 이전 행 삽입 | HARNESS_PASS / UPGRADE_INCOMPATIBLE, H2·PG |
| 실제 팀 보존 환경 | 환경 roster·실제 locations·적용 이력 미제공. 접속하지 않음 | NOT_TESTED / PENDING_TEAM_DB_DECISION |

구버전 demo의 Flyway 이력 `version=2026102702`, `script=V2026102702__version_listing_card_summary.sql`, `checksum=1842460411`, `success=true`가 업그레이드 전후 같다. ownership SQL의 실제 재현 checksum은 옛/새 모두 `-1318655273`이다. 이 checksum은 Git blob/SHA-256과 다른 값이며 팀 DB 이력의 값으로 주장하지 않는다.

옛 일반 경로는 후보의 기본 `validate()`와 `migrate()` 모두 `FlywayValidateException`으로 거부됐다. 메시지의 `2026102702`와 `not resolved locally`를 제한적으로 검사했다. 오류라면 무엇이든 PASS로 처리하지 않았다. 실패 전후 Flyway 이력 전체, 제품/이전 행 전체와 전체 컬럼 목록이 동일함을 검사했다. 진단 성공은 업그레이드 성공이 아니다. 이 환경이 실제 지원 범위라면 별도 호환성 대안 결정이 병합 전 필요하며 이번에 repair/clean/이력 UPDATE를 사용하지 않았다.

## L-01~08 및 F-02/F-03

| ID | 상태 | 근거 |
|---|---|---|
| L-01 | PASS | 실제 일반/pending/demo resolver와 적용 순서 검증. SQL blob 동일. clean 후 최종 main 리소스/두 jar에 새 ownership·기존 demo 존재, 옛 일반 파일 없음 |
| L-02 | PASS | 두 기존 실패 클래스 전체 H2 4건 PASS, 카드 요약 PG 2건 PASS |
| L-03 | PASS | 신규 일반/일반+pending/일반+demo 실제 PG 적용 및 일반+demo H2 |
| L-04 | PASS | legacy target/이력/checksum/데이터·시간 backfill 보존, H2·PG |
| L-05 | HARNESS_PASS / UPGRADE_INCOMPATIBLE | 옛 일반 DB의 기본 validate/migrate 거부와 데이터/이력/스키마 무변경, H2·PG |
| L-06 | PASS | C의 형식·컴파일·전체 test·build, 704 PASS/55 SKIPPED |
| L-07 | PASS | C의 기존 8개 PG/Redis 묶음 109 PASS |
| L-08 | PASS | 수용 운영 코드·기능 기본 OFF·대역/DB 제약 보존 및 이번 전용 자원 제거 |
| F-02 | 이번 C에서 PASS | 전체 build 성공. 과거 실패 기록은 삭제하지 않음 |
| F-03 | 신규/demo 지원 경로 PASS; 옛 일반 INCOMPATIBLE | 무조건적인 기존 DB 호환으로 판정하지 않음 |

## 정확한 실행 형식과 안전 경계

```powershell
.\gradlew.bat clean test --tests '*ListingCardSummaryMigrationTest' --tests '*ListingDemoUpgradeTest' --tests '*H40MigrationCompatibilityTest' --rerun-tasks --no-daemon
.\gradlew.bat spotlessCheck classes testClasses test build --rerun-tasks --no-daemon
# SALE_DRAFT_TEST_DB_*를 이번 전용 PG에 지정
.\gradlew.bat test --tests '*ListingCardSummaryMigrationTest' --tests '*H40MigrationCompatibilityTest' --rerun-tasks --no-daemon
# 이번 전용 PG/Redis의 실제 환경변수를 지정한 기존 8개 묶음
.\gradlew.bat test --tests '*PurchaseRequestPostgresIntegrationTest' --tests '*PurchaseRequestHttpPostgresTest' --tests '*TradeP3PostgresIntegrationTest' --tests '*TradeP4PostgresIntegrationTest' --tests '*JdbcListingHistoryReaderTest' --tests '*ListingHistoryIntegrationHttpTest' --tests '*OwnershipSchemaPostgresIntegrationTest' --tests '*ListingSummaryHttpTest' --rerun-tasks --no-daemon
git diff --check
```

선택 실행은 `P{2,3,4}_TRADE_TEST_ENABLED=true`와 각 `P*_TRADE_TEST_DB_URL/USER/PASSWORD`, `SALE_DRAFT_TEST_DB_URL/USER/PASSWORD`를 사용했다. DB는 `jdbc:postgresql://127.0.0.1:55437/sale_ai_test`, 합성 계정·루프백 trust 인증이며 PASSWORD 인수는 합성 placeholder다. Redis는 `P2_TRADE_TEST_REDIS_HOST=127.0.0.1`, `P2_TRADE_TEST_REDIS_PORT=56387`, `P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true`를 새 전용 인스턴스에만 적용했다. 기본 실행은 opt-in/SALE_DRAFT DB 연결과 외부 키를 제거한 자식 셸에서 수행했다. 외부 AI/S3/메일/Supabase 호출은 없었다.

21:18 KST Rancher Desktop을 hidden 기동하고 21:19:25에 `h40-local-pr-postgres-20261028`(17-alpine, tmpfs, 루프백 55437)과 `h40-local-pr-redis-20261028`(8-alpine, 비영속, 루프백 56387)을 생성했다. 21:26 이후 두 자원을 정상 종료·자동 제거하고 잔여 0 및 두 포트 LISTEN 0을 확인했다. 엔진/다른 사용자 컨테이너는 종료하지 않았다. 스키마 초기화와 Redis FLUSHDB는 이번 생성 자원에만 수행했다.

## 팀 채택 및 원격 제출

팀 DB의 미적용을 가정해 확정하지 않는다. 팀원은 지원 환경 roster·실제 locations, ownership `2026102702` 및 후속 `2026102707`의 적용 여부와 비민감 version/script/checksum/success를 확인해야 한다. 옛 일반 적용 환경이 지원 대상이면 현재 단순 개명안의 비호환을 병합 전에 해결하거나 지원 범위를 결정해야 한다.

팀/포크 workflow를 읽었고 현재 CI는 read-only 권한의 형식·build/test 및 실패 report 업로드만 수행한다. 포크 작업 브랜치 push는 main push 조건에 해당하지 않고 부모 main 대상 PR은 비배포 CI만 유발한다. workflow를 변경하지 않았다. 새 원격 결과·정확한 R·CI 대상 SHA는 실제 제출 후 별도로 재조회해 기록한다. C 이후에는 결과·인계 문서만 추가하며 실행 트리 동일성을 확인한다.

정식 ownership 취소/완료 포트와 상태/ID/토큰/만료/잠금/필수 부수효과, H40-06·R07·P5, 화면 인수·공유 DB 이행·운영 ON·배포는 별도다. 원본 실행 로그/XML은 검수 ZIP으로 전달하며 PR에는 팀원이 읽을 수 있는 이 결과·인계 문서를 연결한다. 실제 공유 URL이 없는 로컬 절대경로를 팀 증거 링크로 제시하지 않는다.
