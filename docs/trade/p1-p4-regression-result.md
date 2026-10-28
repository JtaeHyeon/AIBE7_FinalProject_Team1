# P1~P4 최신 main 회귀검사·랩업 재실행 결과

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
