# P1~P4 최신 main 회귀검사·랩업 재실행 결과

> 2026-10-28 후속 지시 v1.1에서 전용 로컬 PostgreSQL·Redis 실행이 승인되어 R1~R3 보정과 G2 시험을 수행했다.
> 아래 기존 실행 표의 `APPROVAL_BLOCKED`는 당시 사실이며, 현행 후속 결과는 문서 마지막 절을 기준으로 한다.

## 실행 개요

| 항목 | 결과 |
|---|---|
| 실행 ID | `P1-P4-REG-20261028-LATEST-MAIN-PR50-MANUAL` |
| 실행 시각(KST) | 원본 실패 재현 `2026-10-28 14:44:54~14:48:38`, PR #50 반영 baseline·보강본 재검증 `14:56~15:00` |
| 모드 | `BASELINE_AND_TEST_PREP` |
| 최신 팀·개인 main | `5bc3bca3d57e782a4c44bd6096e73b288205bd02` |
| 최신 main 내용 | PR #40 제품 이력 병합 포함 |
| 수동 반영 원본 | 미병합 PR #50 head `73de8689d18af9b5c85ec5a21a6e5480b3b3207a` |
| 수정 baseline | `73de8689d18af9b5c85ec5a21a6e5480b3b3207a` |
| 시험 보강·수정본 | `0a00e819838c28c0dabc3caac0da88713b933fe0` |
| 보강 원본 | `ef4fb12686460e99b931b36313dbaa1f1df6c6ee`를 최신 main 위에 충돌 없이 재적용 |
| 로컬 브랜치 | `test/p1-p4-postmerge-regression-latest-20261028` |
| 외부 실행 승인 | 없음. PostgreSQL·Redis·Docker/Rancher·별도 앱 서버·외부 AI를 실행하지 않음 |
| 원격 쓰기 | 없음. push·PR·merge를 수행하지 않음 |

기존 결과의 고정 T0 `a7b1fe0b089fa1823b154a665c8895fba9763df4` 대신 실행 시 양쪽 원격의
실제 최신 main을 사용했다. 최신 main에서 Flyway 중복을 먼저 재현한 뒤, 미병합 PR #50의 단일 rename
커밋을 수동 반영한 baseline과 보강본을 다시 검사했다. 기존 실행 결과와 증거는 덮어쓰지 않았다.

## 실행 환경과 안전 판독

| 항목 | 실제 값·판정 |
|---|---|
| OS | Windows 10 `10.0.19045`, amd64 |
| Gradle Wrapper | Gradle `9.7.1` |
| 저장소 toolchain | Java 17 |
| 실제 검증 JVM | Eclipse Temurin `17.0.19+10` |
| 최초 환경 판독 | 시스템 기본 Java가 Oracle Java 8로 되돌아가 있어 Gradle 작업 전에 중단. 설치된 JDK 17을 해당 명령 세션에만 지정해 전체 순서를 재시작 |
| `.env` | 새 worktree에 없음 |
| 외부 opt-in | `P2/P3/P4_TRADE_TEST_*`, `AI_TEST_REDIS_PORT`, `SPRING_APPLICATION_JSON`, `JAVA_TOOL_OPTIONS`를 실행 프로세스에서 제거 |
| PostgreSQL·Redis·컨테이너 | `NOT_USED / NOT_RUN` |
| 기능 플래그 | 기본 OFF 유지 |

## 실제 실행 명령과 결과

모든 Gradle 명령은 JDK 17과 외부 opt-in이 제거된 별도 프로세스에서 실행했다.

| 단계 | 명령 | 종료 | 결과 |
|---|---|---:|---|
| 버전 | `gradlew.bat --version --no-daemon` | 0 | Gradle 9.7.1, Launcher/Daemon JVM 17.0.19 |
| baseline 기본 | `gradlew.bat spotlessCheck classes testClasses --no-daemon --console=plain` | 0 | 8 tasks executed, 성공 |
| 원본 main 전체 | `gradlew.bat build -x spotlessCheck --rerun-tasks --no-build-cache --no-daemon --console=plain` | 1 | 463 tests, failure 13, error 0, skipped 36 |
| PR #50 baseline 기본 | `gradlew.bat spotlessCheck classes testClasses --no-daemon --console=plain` | 0 | 성공 |
| PR #50 baseline 전체 | `gradlew.bat build -x spotlessCheck --rerun-tasks --no-build-cache --no-daemon --console=plain` | 0 | 619 tests, failure 0, error 0, skipped 36 |
| 보강본 컴파일 | `gradlew.bat spotlessCheck testClasses --no-daemon --console=plain` | 0 | 신규 PostgreSQL 시험 8건 포함 컴파일 성공 |
| 보강 diff | `git diff --check upstream/main...HEAD` | 0 | 성공 |
| 보강본 기본 | `gradlew.bat spotlessCheck classes testClasses --no-daemon --console=plain` | 0 | 성공 |
| 보강·수정본 전체 | `gradlew.bat build -x spotlessCheck --rerun-tasks --no-build-cache --no-daemon --console=plain` | 0 | 627 tests, failure 0, error 0, skipped 44 |

증거 경로:

- 원본 main 실패 로그·XML·HTML: `build/regression-evidence/g1-baseline-latest-5bc3bca`
- 원본 보강본 실패 로그·XML·HTML: `build/regression-evidence/g1-final-latest-60f3181`
- PR #50 baseline PASS 로그·XML·HTML: `build/regression-evidence/g1-baseline-pr50-73de868`
- 보강·수정본 PASS 로그·XML·HTML: `build/regression-evidence/g1-final-pr50-0a00e81`

## baseline 실패와 수동 수정 판정

13건은 모두 최신 main에 존재하는 일반 Flyway migration 버전 중복으로 초기화 단계에서 실패했다.

- `src/main/resources/db/migration/V2026102002__align_listing_draft_images.sql`
- `src/main/resources/db/migration/V2026102002__create_product_history.sql`

Flyway 메시지는 `Found more than one migration with version 2026102002`이다. baseline과 보강본에서
실패 클래스와 건수가 동일하므로 test-only 보강으로 생긴 회귀가 아니라 최신 main의
`BASELINE / PRODUCT_INTEGRATION` 결함으로 분류한다. 실패 클래스는 다음과 같다.

- AI: `ListingSuggestionHttpTest`, `ListingSummaryHttpTest`, `ListingSummaryRefreshMigrationTest`
- 채팅: `ChatRealtimeIntegrationTest`, `ChatMigrationTest`
- 제품 이력: `HistoryRepositoryTest`
- 판매글: `ListingBrowseHttpTest`, `ListingDemoUpgradeTest` 2건, `ListingDraftHttpTest`,
  `ListingDraftMigrationTest`, `ListingManagementHttpTest`
- 제품: `ProductRepositoryTest`

미병합 PR #50 head `73de8689d18af9b5c85ec5a21a6e5480b3b3207a`는 다른 내용 변경 없이
`V2026102002__create_product_history.sql`을 `V2026102801__create_product_history.sql`로 100% rename한다.
사용자가 확인한 이름은 `V2026102802`였으나 실제 확보한 커밋의 원본은 `V2026102002`다.
이 커밋을 수동 반영한 뒤 일반 migration 버전 중복은 0건이 되었고 baseline 619건과 보강본 627건이 통과했다.
공유·운영 DB 적용 이력은 확인하지 않았으며 실제 병합 여부는 PR #50의 정식 검토를 따라야 한다.

## 거래 회귀와 이월 시험

보강본 XML 기준 거래 패키지는 20개 클래스, 145건이다. failure/error는 0건이며,
외부 opt-in 43건이 SKIPPED되어 실제 실행된 102건은 통과했다. PostgreSQL 시험의 SKIPPED를 PASS로 세지 않는다.

| ID | 시험 메소드 | 작성 | 실행 | 잔여 |
|---|---|---|---|---|
| P3-C01 | `p3C01RollsBackCancellationWhenListingReleaseFailsAfterUpdate` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | 승인된 PostgreSQL |
| P3-C02 | `p3C02RollsBackCancellationWhenAuditInsertFails` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | 승인된 PostgreSQL |
| P3-C03 | `p3C03CancellationAndOtherAcceptanceKeepOneCurrentReservation` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·정식 ownership |
| P3-C04 KEEP | `p3C04NewChangeAndKeepRacePreserveSnapshotsAndInvalidateConfirmations` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·정식 history 연결 |
| P3-C04 CANCEL | `p3C04NewChangeAndCancelRaceLeavesConsistentCancellation` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·정식 history 연결 |
| P3-C05 | `p3C05ListingShutdownAndCancellationNeverReactivateStoppedListing` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·R07 사유 계약 |
| P4-DB02-B 거래 UPDATE | `p4Db02bRollsBackWhenTradeCompletionUpdateFailsAfterWrite` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·정식 completion 포트 |
| P4-DB02-B SOLD UPDATE | `p4Db02bRollsBackWhenSoldUpdateFailsAfterWrite` | `CODE_READY` | `SKIPPED / APPROVAL_BLOCKED` | PostgreSQL·정식 completion 포트 |

최종 SKIPPED 44건은 `AiSuggestionRedisTest` 1건, `PurchaseRequestHttpPostgresTest` 7건,
`PurchaseRequestPostgresIntegrationTest` 15건, `TradeP3PostgresIntegrationTest` 12건,
`TradeP4PostgresIntegrationTest` 9건이다.

## 최신 #40 연결 상태

PR #40의 history Controller·Service·Repository·시험과
`V2026102002__create_product_history.sql`은 수정 전 최신 main에 존재하며, 수동 수정본에서는 migration만
`V2026102801__create_product_history.sql`로 이동했다. 그러나 history 패키지에서
`ReservationChangeService` 또는 `recordChange()` 호출은 확인되지 않았다. 따라서 제품 이력 등록에서
P3 변경 review를 같은 트랜잭션으로 생성하는 정식 연결은 여전히 `DEPENDENCY_BLOCKED`다.

`InternalTransferPendingWriter`, `InternalTransferCancellationPort`, `InternalTradeCompletionPort`도
운영 구현 공급이 확인되지 않았고 인터페이스와 거래 서비스 소비 지점만 존재한다. 기존 fixture 시험을
정식 ownership/completion 인수 완료로 확대하지 않는다.

## RG-01~26 랩업

| 범위 | 이번 판정 |
|---|---|
| RG-01~10 | PR #50 rename 반영 후 형식·컴파일과 실행 가능한 Mock/H2/단위·HTTP/Repository 시험 통과 |
| RG-11 | PostgreSQL 동시 수락은 `CODE_READY / SKIPPED / APPROVAL_BLOCKED` |
| RG-12~18 | 실행 가능한 단위 거래 시험은 통과. P3-C01~C05는 위 표와 같이 DB 실행 대기 |
| RG-19~25 | 실행 가능한 P4 단위 시험은 통과. P4 PostgreSQL·정식 completion/ownership은 대기 |
| RG-26 | 공개 상세·AI·제품 Repository의 G1 시험 통과. 정식 ownership/history/document 인수는 `DEPENDENCY_BLOCKED` |

## 종료 판정

- 최신 main 원본 실패를 재현하고 PR #50 rename을 수동 반영한 baseline과 test-only 보강본의 회귀·랩업 순서를 모두 재실행했다.
- 형식, 컴파일, testClasses, diff 검사는 성공했다.
- 수동 수정 baseline 전체 build는 619건 중 failure/error 0, 보강본 전체 build는 627건 중 failure/error 0으로 통과했다.
- 외부 DB·Redis 승인이 없어 G2는 `NOT_RUN / APPROVAL_BLOCKED`, 정식 연결 G3는 `DEPENDENCY_BLOCKED`다.
- 따라서 이번 결과는 **PR #50 rename을 수동 반영한 G1 회귀 PASS**다. 미병합 변경을 전제로 하므로 정식 main PASS나 전체 통합 인수 완료로 확대하지 않는다.
- 컨테이너·DB·Redis·외부 서비스·공유 자원을 만들거나 접속하지 않았고 정리 대상이 없다.

## 후속 v1.1 — R1~R3 보정 및 승인 로컬 회귀

### 실행 대상과 기준

| 항목 | 후속 실행 값 |
|---|---|
| 실제 작업 경로 | `C:/Workspace/AIBE7_FinalProject_Team1-regression-followup-20261028` |
| 로컬 브랜치 | `test/p1-p4-regression-followup-20261028` |
| 시작 SHA | `30877cfaa32aaf4d0bd363bf65231e40aed8e7ee` |
| 시험 보정 SHA | `e56683c78c50579090ab7049c0003f90d7ecfd5f` |
| 원격 main 시작 | 팀·개인 `5bc3bca3d57e782a4c44bd6096e73b288205bd02` |
| 원격 main 종료 | 팀 `b21c42e96eb6745f95d8f9f764a3a99075d9c35a`, 개인 `5bc3bca3d57e782a4c44bd6096e73b288205bd02` |
| rename 원본 | 시작 시 미병합 PR #50 head `73de8689d18af9b5c85ec5a21a6e5480b3b3207a`, 종료 전 팀 main 병합 확인 |
| Java | Eclipse Temurin `17.0.19+10` |
| Gradle Wrapper | `9.7.1` |
| 승인 | 전용 폐기 가능 PostgreSQL·Redis 및 필요한 기존 Rancher Desktop 사용 승인 |

검수본 `30877cf`는 기존 worktree에 그대로 보존하고 새 후속 worktree에서 작업했다. 일반 migration에는
`V2026102002__align_listing_draft_images.sql`과 `V2026102801__create_product_history.sql`이 함께 있으며,
소스와 `build/resources/main` 모두 이전 이름 `V2026102002__create_product_history.sql`이 남지 않았다.

종료 직전 팀 main은 PR #50 merge commit `b21c42e`로 전진했다. 팀 main과 후보의
`V2026102801__create_product_history.sql` blob은 동일하며 PR #50 head와 merge commit 사이 추가 내용 차이는 없다.
따라서 rename을 중복 적용하거나 진행 중 후보에 merge하지 않았고, 팀 main 대비 후보 차이는 시험·결과 문서 3개뿐이다.

Flyway 위치별 버전 중복은 각각 0건이다.

- 일반: `classpath:db/migration`
- P2 pending: `classpath:db/pending/trade-p2` — `V2026102103__create_purchase_requests.sql`
- P3 pending: `classpath:db/pending/trade-p3` — `V2026102602__create_transaction_change_reviews.sql`

일반·pending 위치는 시험에서 단계적으로 합쳐 실행했으며 운영 Flyway locations나 migration 내용을 변경하지 않았다.

### R1 — 정식 history 스키마 정합화

- P3/P4 시험의 `create table product_events` fixture를 제거하고 일반 migration의 실제 테이블을 사용했다.
- P3 `SPEC_CHANGE` fixture에 실제 소유자 작성자, 전후 사양 JSON, 제목·상태를 채워 author/spec CHECK를 유지했다.
- P4 제품 변경 fixture도 같은 실제 제약을 사용했다.
- P4 완료 fixture는 구매 요청 ID를 사건 컬럼에 직접 저장하지 않고, 실제 `ownership_transfers.id`를
  `product_events.transfer_request_id`로 저장하도록 변경했다.
- TRANSFER 단일성·롤백 assertion은 `product_events.transfer_request_id`와 `ownership_transfers.id` JOIN으로 확인했다.
- 기존 판매 중지 fixture는 `CANCELLED` 전환 시 `active_slot = null`을 함께 반영해 실제 listing CHECK를 유지했다.
- test-only ownership/completion 대역은 `src/test`에만 남겼으며 정식 운영 구현으로 판정하지 않았다.

### R2 — 최초 KEEP 경합

- 신규: `p3C04FirstKeepAndNewChangeRacePreserveSnapshotsAndReservation`.
- 미응답 review의 최초 KEEP와 새 사건 등록을 유한 latch/timeout 경합으로 실행했다.
- 기존 사본·최초 KEEP·결정 시각, 새 사건의 미확인 사본, 양측 완료 확인 무효화, RESERVED/PENDING 유지,
  TRANSFER 사건 미생성을 실제 PostgreSQL에서 확인했다.
- 기존 재요청 시험은 `p3C04NewChangeAndRepeatedKeepPreserveSnapshotsAndInvalidateConfirmations`로 목적을 명확히 해 보존했다.
- 두 시험 모두 HistoryService/API가 아닌 `ReservationChangeService.recordChange()` 직접 호출의 부분 연결 시험이다.

### 실제 실행 결과

| 실행 | 결과 | 통과 | 실패 | 오류 | 건너뜀 | 증거 |
|---|---|---:|---:|---:|---:|---|
| P3 최초 실행 | FAIL | 12 | 1 | 0 | 0 | `build/regression-evidence/followup-p3-postgres` |
| P3 fixture 보정 재실행 | PASS | 13 | 0 | 0 | 0 | `build/regression-evidence/followup-p3-postgres-rerun` |
| P4 PostgreSQL | PASS | 9 | 0 | 0 | 0 | `build/regression-evidence/followup-p4-postgres` |
| P2 저장소 PostgreSQL | PASS | 15 | 0 | 0 | 0 | `build/regression-evidence/followup-p2-postgres` |
| P2 HTTP/STOMP PostgreSQL·Redis | PASS | 7 | 0 | 0 | 0 | `build/regression-evidence/followup-p2-http-postgres-redis` |
| 최종 기본 build | PASS | 583 | 0 | 0 | 45 | `build/regression-evidence/followup-g1-final-e56683c` |

P3 최초 실패는 `listingShutdownNeverReactivatesCancelledListingAndCancelsAllRequests`의 setup이
`CANCELLED` 전환 시 `active_slot`을 유지해 `ck_listings_open_slot`을 위반한 `TEST_HARNESS` 결함이다.
실제 CHECK를 약화하지 않고 fixture를 보정했으며 전체 P3 재실행으로 13건 통과를 확인했다.

최종 기본 build의 45건 SKIPPED는 외부 opt-in을 해제한 별도 실행의 예상 결과다.
동일 후보에서 별도로 실제 실행한 P2 22건, P3 13건, P4 9건은 위 표의 XML로 구분한다.

주요 실행 명령은 다음과 같다.

- `gradlew.bat spotlessCheck classes testClasses --no-daemon --console=plain`
- `gradlew.bat build -x spotlessCheck --rerun-tasks --no-build-cache --no-daemon --console=plain`
- `gradlew.bat test --tests "org.example.springtestci.trade.repository.PurchaseRequestPostgresIntegrationTest" ...`
- `gradlew.bat test --tests "org.example.springtestci.trade.repository.TradeP3PostgresIntegrationTest" ...`
- `gradlew.bat test --tests "org.example.springtestci.trade.repository.TradeP4PostgresIntegrationTest" ...`
- `gradlew.bat test --tests "org.example.springtestci.trade.controller.PurchaseRequestHttpPostgresTest" ...`

### 전용 로컬 자원 사용과 정리

| 자원 | 식별·사용 |
|---|---|
| Rancher Desktop | 작업 전 Docker API 중지. 작업에서 기동, Docker client `29.6.2-rd`, server `29.5.3` |
| PostgreSQL | `postgres:17-alpine`, digest `sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b`, 컨테이너 `c298935ec538…`, `127.0.0.1:55432`, tmpfs |
| Redis | `redis:8-alpine`, digest `sha256:47b08295737773de3b711fb86f13ae802f5074e96a9a2d29f5f80164ac08c046`, 컨테이너 `26d0532cbe94…`, `127.0.0.1:56379`, tmpfs |

시험 종료 시 거래 시험 schema는 0개, Redis DB size는 0이었다. 두 전용 컨테이너와 시험 자격 증명 파일을
삭제했으며 전용 volume은 생성하지 않았다. Rancher 기동 후 작업에서 생성하지 않은 기존 컨테이너 17개가
자동 실행된 상태를 확인해 엔진 전체는 임의 종료하지 않았다. 상세 정리 로그는
`build/regression-evidence/followup-local-resources/resource-cleanup.log`에 있다.

### 후속 종료 판정과 이월

- R1/R2/R3 보정, 컴파일, 기본 build, 승인된 P2/P3/P4 PostgreSQL 및 P2 HTTP/STOMP 회귀를 완료했다.
- P3-C01~C05와 P4-DB02-B는 이번 전용 PostgreSQL에서 실제 실행되어 PASS했다.
- PR #40 history 코드가 P3 review를 같은 트랜잭션에서 호출하는 정식 H40 연결은 여전히 미제공이다.
- 정식 ownership/completion 구현, 공유·보존 DB 적용 이력/checksum, 화면 인수, P5는 별도 이월이다.
- 실제 AI·S3·Supabase·메일, 공유 DB, Flyway repair/clean, 배포 서버는 사용하지 않았다.
- 이 결과는 **격리 로컬 환경의 거래 측 G1/G2 1차 회귀 PASS**이며 공유 환경·정식 외부 연결 인수 완료가 아니다.
