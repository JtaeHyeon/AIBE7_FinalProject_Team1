# P4 양측 거래완료 구현 결과

## 현재 상태

**P4 거래 측 핵심 구현 검수 수용 / 관련 선택 검사 작성자 통과 / P4 DB 시험 helper·일부 시나리오 보강, 지정 저장 실패 세부 이월 / 정식 외부 연동·DB 실행·통합 인수 대기. P3 이월 유지.**

## 기준과 구현

| 항목 | 결과 |
|---|---|
| 시작 SHA | `0c3b91f8218d56f8b3707ec6127544113fe22814` |
| 작업 브랜치 | `feature/trade-p4-completion` |
| 기능 기본값 | `app.features.trade-p4.enabled=false` |
| API | 완료 context GET, 내 완료 확인 POST 구현 |
| 잠금 순서 | 비잠금 요청·원래 방 확인 → 제품·판매글 → 판매글별 요청 ID 순 잠금 |
| 완료 상태 | 요청 `ACCEPTED` 유지, 거래 `COMPLETED`, 판매글 `SOLD`, 다른 `PENDING`만 `REJECTED` |
| 정식 외부 처리 | `InternalTradeCompletionPort` 계약만 제공, 운영 Bean 미제공 |
| DDL | 기존 P2 컬럼·상태를 재사용하여 P4 DDL 추가 없음 |

완료 재요청은 원래 당사자 권한만 확인하고 현재 소유자·facts 버전·보관 상태를 다시 요구하지 않는다. 진행 중 확인은 실제 `products.facts_version`, READY·비샘플 공개 이력과 미확인 변경 0건을 독립 검사한다. 마지막 확인은 거래·판매글·남은 요청·외부 완료·감사를 같은 Spring 트랜잭션에서 처리한다.

## 검증 결과

최종 실행 결과는 아래와 같다. PostgreSQL·Redis·Rancher·Docker·로컬 API는 기동하지 않았다.

| 검사 | 상태 |
|---|---|
| `gradlew.bat spotlessCheck classes testClasses` | 성공 |
| P4 및 직접 영향 단위·Controller·H2 저장소 시험 15개 클래스 | 100건 성공, 실패·오류·건너뜀 0 |
| `TradeP4PostgresIntegrationTest` | ID별 작성 범위는 아래 표 참조 / 전체 NOT_RUN — `testClasses` 컴파일 성공, DB 미기동 |
| 전체 교차 모듈 회귀 | 범위 외 / NOT_RUN |

최종 실행 시각은 2026-10-26 18:47 KST이며 작업 트리 기준이다. 선택자는 `PurchaseRequestTest`, `JdbcPublishedListingAccessTest`, `JdbcPurchaseRequestCompletionTest`, `TradeP4ConcurrencyHelperTest`, `TradeCompletionServiceTest`, `TradeCompletionControllerTest`, `TradeP4FeatureFlagTest`, `TradeCancellationServiceTest`, `ReservationChangeServiceTest`, `TradeListingCancellerTest`, `TradeControllerTest`, `TradeP3FeatureFlagTest`, `PurchaseRequestServiceTest`, `PurchaseRequestControllerTest`, `PurchaseRequestFeatureFlagTest`다. 결과 XML은 `build/test-results/test`에 있다.

## P4 PostgreSQL 시험 준비 상태

아래 상태는 코드 작성 범위이며 실행 성공을 뜻하지 않는다. 모든 항목의 실제 PostgreSQL 실행은 `NOT_RUN`이다.

| ID | 작성 상태 | 현재 코드의 검증 범위·잔여 |
|---|---|---|
| P4-DB01 | CODE_READY / NOT_RUN | 순차 양측 확인 뒤 거래·SOLD·이전·소유자·TRANSFER·PRIVATE·PENDING 거절·감사를 검사 |
| P4-DB02-A | CODE_READY / NOT_RUN | 완료 포트·감사 실패에서 마지막 확인, 거래·판매글, PENDING, 이전·소유자, TRANSFER, PUBLIC, 증빙, 초안, 완료 감사 복원을 검사 |
| P4-DB02-B | CODE_TODO / NOT_RUN | 거래 완료 UPDATE 실패와 SOLD 저장 실패를 실제 PostgreSQL 트랜잭션에서 주입하는 세부 시나리오 |
| P4-DB03 | CODE_READY / NOT_RUN | 양측 동시 확인과 완료 재요청에서 이전 이력·감사 단일성을 검사 |
| P4-DB04 | CODE_READY / NOT_RUN | 실제 `TradeCancellationService`와 완료 경합, 결과별 판매글·이전·소유자·공개 상태·이력·감사를 검사. 예상 경합은 `TRADE_CONFLICT`만 허용 |
| P4-DB05 | CODE_READY / NOT_RUN | P3 pending 제약과 실제 `ReservationChangeService.recordChange()` test fixture를 사용해 변경 review·확인 초기화와 완료 경합을 검사. 정식 history 원천 연결은 P4-I02 유지 |
| P4-DB06 | CODE_READY / NOT_RUN | 상위 판매글 CANCELLED 전환 뒤 실제 `TradeListingCanceller`를 호출해 완료 경합, 취소 메타데이터와 다른 PENDING 정리를 검사 |
| P4-DB07 | CODE_READY / NOT_RUN | 기존 상태 조합과 PENDING 정리를 검사. 신규 P4 DDL을 작성하지 않았으므로 신규 제약 검증은 해당 없음 |

## P4 정식 연결·인수 대기

| 관리 ID | 상태 | 필요한 입력·완료 기준 |
|---|---|---|
| P4-I01 | DEPENDENCY_BLOCKED | `InternalTradeCompletionPort` 정식 구현, 동일 트랜잭션·멱등·유일 제약 계약과 제공 SHA |
| P4-I02 | DEPENDENCY_BLOCKED | READY·비샘플 실제 공개 history와 factsVersion 증가·P3 review/확인 무효화의 같은 제품 잠금 계약 |
| P4-DB01~07 | 혼합 / NOT_RUN | 위 ID별 표의 CODE_READY·CODE_TODO 범위를 유지하고 승인된 PostgreSQL에서 실행 필요 |
| P4-UI01 | 별도 작업 대기 | 양측 확인·상대 대기·변경 재확인·오류/재시도 화면 인수 |
| P4-INT01 | APPROVAL_BLOCKED + DEPENDENCY_BLOCKED | 정식 ownership/history와 승인 스키마에서 완료·취소 경합, 공개 종료, 증빙 권한 통합 인수 |

## P3 미작성 시험 이월

| 관리 ID | 상태 | 보존할 검증 |
|---|---|---|
| P3-C01 | CODE_TODO | 예약 취소 중 판매글 ACTIVE 복구 저장 실패 전체 롤백 |
| P3-C02 | CODE_TODO | 예약 취소 중 감사 INSERT 실패 전체 롤백 |
| P3-C03 | CODE_TODO | 예약 취소와 다른 PENDING 요청 수락 경합 |
| P3-C04 | CODE_TODO | 새 이력 등록과 KEEP/CANCEL 경합 |
| P3-C05 | CODE_TODO | 판매 중지와 예약 취소 경합, CANCELLED 판매글 비복구 |

## 준비된 P3 PostgreSQL 시험 이월

다음 `TradeP3PostgresIntegrationTest` 메소드는 모두 **CODE_READY / NOT_RUN** 상태를 유지한다.

- `commitsReservationCancellationAcrossTradeListingTransferAndAudit`
- `rollsBackEveryCancellationWriteWhenOwnershipCancellationFails`
- `simultaneousCancellationKeepsFirstMetadataAndSingleAudit`
- `storesJsonSnapshotOnceAndKeepsOtherReviewsUnreviewed`
- `listingShutdownNeverReactivatesCancelledListingAndCancelsAllRequests`
- `cancellationMetadataConstraintRejectsNullGapsAndKeepsValidCancellationStates`

## 기존 외부 대기 유지

`P2-I01`, `P3-I01`~`P3-I04`, `DB-I01`~`DB-I02`, `INT-I01`, `UI-I01`, `DEPLOY-I01`은 `p3-result.md`와 P4 명세 부록 A의 상태를 그대로 유지한다. P4 구현이나 Mock/H2 시험을 정식 ownership/history 공급, 실제 DB 실행, 화면·배포 승인으로 간주하지 않는다.
