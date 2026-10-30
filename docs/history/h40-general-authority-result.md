# H40 일반 Migration 기준 복구 (v1.3)

## 현재 판정과 범위

**LOCAL_PASS.** 일반 `db/migration`을 스키마 기준으로 사용한다. 일반 ownership `2702`와 카드 요약 `2802`가 현재 계약이며, 이전 `CURRENT_BASELINE_ADOPTED`의 `2702.1` 채택은 역사 기록이다. 이 보정은 PR #55의 H40 기능·R1 공개 허용 필드·worker 경합 오류 분리·P1~P4 수용 내용을 유지한다.

| 기준 | 값 |
|---|---|
| 실제 시작점 S | `d3a3a20e9b73eebc3680433c44ae0bfed807a8e5` |
| 최종 코드·시험 후보 C | `dab3b3213deb95cab511b7200a1689c4fb77264e` |
| 시작 PR base | `f5ad1025abb86a79cb8ff35d7178c8e80aade151` |
| 작업 트리 | 기존 `AIBE7_FinalProject_Team1-h40-final-20261028` 재사용 |
| 제출 | 기존 [PR #55](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/pull/55), 개인 포크 `feat/h40-history-integration-20261028` |
| 보고 R·원격 head·CI | 제출 후 검수 ZIP의 `MANIFEST.json` / `PR_INFO.json`에 정확한 SHA·상태·run URL 기록 |

## 변경과 지원 제한

- ownership 파일을 `V2026102702__create_ownership_tables.sql`로 복구했다. 원문 Git blob은 `bd1a6de9dbde5acae7bd2db5670089d5187ac383`으로 동일하다.
- 충돌 demo 카드 요약만 `archive/V2026102702__version_listing_card_summary.sql.txt`로 옮겼다. 원문 blob `a0a0666bd08145baf6586e2388829ee308cb49f2`를 보존하며 실행 locations에 연결하지 않는다. 나머지 세 demo fixture는 유지한다.
- 일반 `2706/2707/2801/2802`, pending SQL, 운영 Java, Gradle 설정, 기능 기본값은 S와 동일하다. 검증 해제·ignore 패턴·새 baseline/out-of-order·checksum 재기록을 추가하지 않았다.
- 새 시연 DB는 `${java.io.tmpdir}/pastport-sale-draft-general-r02`를 사용한다. 기존 `r01` 파일 DB·이미지 경로·사용자 데이터를 삭제하거나 자동 변환하지 않는다.
- 과거 demo 카드 요약 `2702` 및 시험 ownership `2702.1` 적용 DB의 자동 전환은 지원하지 않는다. 별도 보존 환경이나 격리된 신규 DB를 사용한다.
- 사용자 제공 온라인 네 행은 전체 이력·스키마가 아니다. 구매 요청 온라인 `2701`과 저장소 pending `2103`의 차이는 별도 확인 항목이며 임의 개명·복제·일반 편입하지 않았다. 이 보정에서는 온라인/공유 DB에 접속하거나 변경하지 않았다.

## 시험 계약 변경

| 기존 목적 | 현재 검사 또는 제외 근거 |
|---|---|
| 일반 ownership `2702` 적용 DB를 비호환으로 거부 | 고정 이전 SQL을 `2707`까지 정상 migrate하고 합성 제품·이전 행을 준비한다. 적용 경계 `2707`을 validate한 뒤 실제 일반 locations로 migrate, 전체 validate, 재실행 0건을 확인한다. 기존 이력 행·checksum·제품·이전·ownership 컬럼·제약 및 pending 유일성을 보존한다. |
| 신규 일반+demo에서 demo 카드 `2702`와 ownership `2702.1` 공존 | 일반 ownership `2702`, 카드 요약 `2802` 적용과 퇴역 두 이력의 부재를 검사한다. |
| 옛 demo 카드 `2702` 자동 업그레이드 보존 | 사용자가 폐기한 지원 계약이다. 현재 일반 ownership `2702`+demo fixture 출발 상태로 대체하여 긴 요약·제목·기존 이력·checksum 및 `2706` 시간 backfill 보존을 검사한다. |
| 일반 `2802` 이전 긴 요약 보존·format 0·90자 제약·제목 보존 | 기존 일반 경로 시험을 유지한다. |
| ListingDemoUpgradeTest의 초안·이미지·계정 보존 2건 | 변경 없이 유지한다. |

이력표 수동 삽입·수정, 예외 무시, 전체 demo 시험 제외를 사용하지 않는다. 정식 외부 AI를 호출하지 않으며 기존 시험 대역과 DB_PARTIAL 포트 범위를 유지한다.

## 이번 실행

JDK 17.0.19와 저장소 wrapper를 사용했다. 자식 프로세스 환경은 시스템 실행에 필요한 변수만 허용하고 실제 DB/AI/S3/메일 키 등을 제거했다. 빈 `DOTENV_PATH`를 명시했으며 Gradle test의 기존 `build/disabled-env/.env` 차단도 유지한다. 사용자 `.env`를 복사하거나 로딩하지 않았다.

| 실행 ID | 결과 |
|---|---|
| basic | 중간 후보 컴파일 실패. AssertJ에 없는 메소드를 지원 assertion으로 수정. 시험 실행 전 실패. |
| basic-r2 | 중간 후보 Spotless 줄바꿈 검사 실패. 형식 보정 후 재실행. 시험 실행 전 실패. |
| basic-r3 | 759건 중 703 PASS / 1 FAIL / 55 SKIPPED. 미적용 후속 버전이 있는 전체 validate 호출을 적용 경계 validate → migrate → 전체 validate 순서로 수정. |
| basic-r4, C | `clean spotlessCheck test build --rerun-tasks --no-daemon` 성공. 759건 중 704 PASS / 55 SKIPPED, 실패·오류 0. |
| selected, C | 전용 PostgreSQL·Redis 영향 회귀 10개 클래스 113/113 PASS, 실패·오류·SKIPPED 0. |

기본 SKIPPED 55건 중 54건은 선택 실행의 동일 classname/testcase PASS에 대응한다. 나머지는 `AiSuggestionRedisTest.atomicLimitsAndLeaseReleaseAcrossConcurrentRequests`이며 별도 NOT_RUN이다. 기본 skip을 PASS로 계산하지 않는다. 실행별 로그·종료 코드·XML은 build 밖 검수 자료에 분리 보존한다. 과거 C0 실행 수치를 이번 결과로 재사용하거나 중복 합산하지 않는다.

전용 자원은 `h40-general-authority-postgres-20261029`(PostgreSQL 17, tmpfs, `127.0.0.1:55439`, DB `sale_ai_test_h40_general`)와 `h40-general-authority-redis-20261029`(Redis 8, `127.0.0.1:56389`)다. 실제 container label·포트·DB·루프백 접속을 확인했다. Windows Docker 프록시의 간헐적 Hyper-V timeout 때문에 자원 관리는 WSL 내부 Docker CLI를 사용했고, Java 시험은 확인한 루프백 포트로 접속했다. 시험 후 해당 두 컨테이너와 부속 익명 볼륨만 정상 종료·제거했으며 기존 DB·컨테이너는 삭제하지 않았다.

재현은 `run-tests.ps1`의 환경 차단과 이번 생성 자원 지정이 전제다. 사용자 `.env`를 로드한 셸에서 아래 명령만 복사 실행하지 않는다. 정확한 선택자·환경과 실행 명령은 검수 ZIP의 실행별 `command.json` 및 스크립트에 있다.

```powershell
.\gradlew.bat clean spotlessCheck test build --rerun-tasks --no-daemon
.\gradlew.bat test --tests '*ListingCardSummaryMigrationTest' --tests '*H40MigrationCompatibilityTest' --tests '*PurchaseRequestPostgresIntegrationTest' --tests '*PurchaseRequestHttpPostgresTest' --tests '*TradeP3PostgresIntegrationTest' --tests '*TradeP4PostgresIntegrationTest' --tests '*JdbcListingHistoryReaderTest' --tests '*ListingHistoryIntegrationHttpTest' --tests '*OwnershipSchemaPostgresIntegrationTest' --tests '*ListingSummaryHttpTest' --rerun-tasks --no-daemon
```

## 완료표

| ID | 상태·근거 |
|---|---|
| G-01 | PASS: 원문 blob 일치, clean 후 최종 JAR에 ownership `2702` 한 개. `2702.1`/퇴역 demo 실행 파일 없음. |
| G-02 | PASS: 지원 일반+demo JAR 버전 중복 없음. 일반 신규 및 일반+demo 생성·카드 `2802` 적용 시험 통과. |
| G-03 | H2/PG PASS: 실제 고정 이전 일반 DB에서 validate/migrate·이력·제품·이전·제약 보존 및 ownership 미재실행. |
| G-04 | H2/PG PASS: 일반 요약 보존·제약 및 일반+demo backfill. 기존 demo 초안·이미지·계정 두 시험은 H2에서 유지·통과. |
| G-05 | PASS: C의 기본 형식·전체 test·build. 중간 실패는 위와 같이 별도 기록. |
| G-06 | PASS: 선택 PostgreSQL·Redis 113/113. 기존 8개 회귀 109건과 Migration 2개 클래스 4건. 외부 AI 호출 없음. |
| G-07 | PASS: 기능·pending·후속 SQL 보존, 공유 DB 무접속, 이번 생성 자원만 정리. |
| G-08 | 기존 PR 갱신·원격 head/CI는 검증 완료 후 제출 자료에 기록. |

## 후속 내부 연결 기준

최종 코드 C는 위 SHA이며 보고 R과 실제 원격 제출 head는 검수 `MANIFEST.json`/`PR_INFO.json`을 기준으로 한다. C 이후 보고 문서만 추가했다면 실행 트리 동일성으로 결과를 연결하고 성공한 시험을 불필요하게 반복하지 않는다.

`Pastport_NextTask_Current_Ownership_Internal_Integration_v1.0.md`의 시작점은 이번 검증된 **보고 R**로 갱신한다. `d3a3a20`은 과거 참고점이다. 해당 명세 §0.2/§0.3/§3의 `2702.1` 보존·일반 `2702` 미지원 조항은 **일반 ownership `2702`/카드 요약 `2802`, 과거 demo 카드 `2702` 자동 업그레이드 미지원**으로 대체한다. 세 내부 포트·원자성·권한·OI 인수 범위는 유지한다.

순서는 이번 보정 마감 → 별도 브랜치의 내부 ownership 연결 구현·인수 → 기존 P5 재사용/부족분 보완이다. 기존 진행 브랜치는 보존하고 이번 보정 delta만 출처와 함께 반영한다. 이 결과는 내부 포트 신규 구현·P5·H40-06·R07 전체·공유 DB 적용·병합·배포 완료를 뜻하지 않는다.
