# P2 A단계 구현·재검수 결과

> 이 문서는 A단계 종료 당시의 실행 이력을 보존한다. pending DDL과 테스트 Writer 상태는 이후 B단계에서 해소됐으며, 현재 판정은 `p2-stage-b-result.md`와 `p2-stage-b-migration-plan.md`를 기준으로 한다.

## 기준과 통합 상태

- 최초 재검수 대상: `b53ffe785dd2649b450deb5a334687a646baa5f7`
- 이번 보완 검수 기준: `527df0bc951c64650bb24a85cbbdc99df965082c`
- A단계 종료 승인 기준: `39d395b1d39c58f9d016009e4e5234a8ec16c825`
- 통합한 `upstream/main`: `b43695d2ad130d975a1f08fbffbc1cd14403a2c3`
- 통합 merge commit: `9174bf2`
- 작업 브랜치: `fix/trade-p2-stage-a-recheck`
- 작업 방식: 원래 브랜치를 보존하고 최신 `upstream/main`에서 전용 브랜치를 만든 뒤 `7a01de7`, `b53ffe7` 변경을 커밋 없이 이관했다.
- `39d395b` 검수에서 A단계 잔여 보완 종료와 PR 진행 가능 판정을 받았다.
- 이후 `upstream/main`의 게시 판매글 구현을 병합하고 통합·회귀 검사를 다시 수행했다.
- `upstream/main`의 Flyway 버전과 충돌한 미적용 pending DDL은 `V2026102103__create_purchase_requests.sql`로 재번호했다.
- 최신 제품 Migration의 `products.manufacturer_normalized NOT NULL`을 두 PostgreSQL fixture에 반영했다.
- 거래 Controller는 자체 `Long.parseLong(authentication.getName())` 대신 공통 `CurrentUserIdResolver`를 사용한다.
- 기능은 기본 off이며 pending 거래 DDL은 기본 Flyway 위치 밖에 유지한다.

## R1~R4 판정

| ID | 판정 | 근거 |
|---|---|---|
| R1 | 수정·실행 통과 | `ACCEPTED` 분기에 `transaction_status IS NOT NULL`을 추가했다. 실제 PostgreSQL에서 ACCEPTED/NULL INSERT·UPDATE가 SQLSTATE `23514`, `ck_purchase_requests_status_pair`로 거부되고 허용 조합은 저장됨을 확인했다. |
| R2 | 수정·실행 통과 | `upstream/main` 최신 SHA에서 전용 브랜치를 만들고 새 제품 필수 컬럼과 공통 사용자 식별 경계를 반영했다. 원래 사용자 브랜치와 커밋은 변경하지 않았다. |
| R3 | 수정·실행 통과 | 아래 P2-01~30 대조표의 단위·실제 PG·실제 HTTP·실제 STOMP 검증을 연결했다. 문의자의 수락 시도도 403과 DB 불변으로 직접 확인했다. |
| R4 | 수정·실행 통과 | 제품별 PENDING UNIQUE와 실제 제품 소유자·잠금을 반영했다. 알려진 PENDING 이전 중복만 409로 변환하고 다른 DB 장애는 500으로 유지한다. |

## PostgreSQL fixture 계약

- `listings`는 최신 운영 Migration을 사용한다. A단계 테스트 어댑터가 요구하는 `current_owner_id`만 임의 스키마에 추가하고 실제 소유자 판정은 계속 `products.owner_id`를 사용한다.
- 게시글 잠금 시 제품을 먼저 잠그고 게시글을 잠근다. 현재 소유자는 `listings.current_owner_id` 복사 값이 아니라 `products.owner_id`에서 읽는다.
- `ownership_transfers`는 `purchase_request_id UNIQUE`와 `status = 'PENDING'`인 `product_id` 조건부 UNIQUE를 가진다.
- 내부 이전 insert와 게시글 reserve UPDATE는 영향 행 수가 정확히 1인지 검사한다.
- 테스트 Writer는 SQLSTATE `23505`와 `uq_ownership_transfers_pending_product`가 모두 일치할 때만 `TRADE_CONFLICT`로 변환한다.
- 일반 실행에는 fixture 어댑터나 no-op 구현을 등록하지 않는다.

## P2-01~30 대조표

표의 결과는 아래 PostgreSQL 전용 실행에서 모두 `PASS`였다. `Service`는 `PurchaseRequestServiceTest`, `PG`는 `PurchaseRequestPostgresIntegrationTest`, `HTTP`는 `PurchaseRequestHttpPostgresTest`를 뜻한다.

| ID | 테스트 클래스·메서드 | 핵심 assertion | 결과 |
|---|---|---|---|
| P2-01 | Service `createsPendingRequestForRoomInquirer`, HTTP `createsPurchaseRequestThroughRunningHttpServerAndBearerJwt` | PENDING/NULL 한 행, 201, ACTIVE 상태 응답 | PASS |
| P2-02 | Service `reusesExistingPendingRequestWithoutLockingListing`, Controller `returnsOkForReusedPendingRequest` | 동일 ID, 저장·잠금 없음, 200 | PASS |
| P2-03 | Service `reusesExistingPendingRequestWithoutLockingListing` | 게시글 RESERVED여도 기존 PENDING을 그대로 반환하고 신규 생성 없음 | PASS |
| P2-04 | Service `hidesForeignRoomAsNotFound`, `returnsDetailOnlyToRoomParticipant`; Repository actor 조건 | 비참여자 404 경계, actor별 SENT/RECEIVED 범위 | PASS |
| P2-05 | Service `rejectsSellerAttemptToCreateBuyerRequest`, `rejectsWrongRolesAndReprocessingOfTerminatedRequest`, `rejectsBuyerAcceptanceWithoutChangingRequestOrDependencies`; HTTP `rejectsBuyerAcceptanceWithoutChangingPersistentState` | 판매자 생성·문의자 수락/거절 역할 오류, 문의자 수락 403, DB 변경 없음 | PASS |
| P2-06 | Service `rejectsMismatchedRoomListingInactiveListingAndProductOwner`, `rejectsListingWhoseFixedSellerDiffersFromRoomSeller` | 방/URI listing 및 고정 판매자 불일치 거부 | PASS |
| P2-07 | Service `rejectsNewRequestForEveryNonActiveListing` | RESERVED/SOLD/CANCELLED 각각 TRADE_CONFLICT, 저장 없음 | PASS |
| P2-08 | PG `rejectsCreateAndAcceptWhenProductOwnerDiffersFromListingSeller` | 실제 `products.owner_id` 변경 후 생성·수락 거부, 부분 변경 없음 | PASS |
| P2-09 | Service `buyerCancelsAndSellerRejectsPendingRequests`; PG `cancellingAndRejectingOtherPendingRequestsPreservesExistingReservation` | 문의자 취소가 CANCELLED/NULL이고 게시글 예약에 영향 없음 | PASS |
| P2-10 | 같은 메서드 | 판매자 거절이 REJECTED/NULL이고 게시글 예약에 영향 없음 | PASS |
| P2-11 | PG `cancellingAndRejectingOtherPendingRequestsPreservesExistingReservation` | B2 취소·B3 거절 후 B1 ACCEPTED/RESERVED, 게시글 RESERVED, 이전 1건 유지 | PASS |
| P2-12 | PG `commitsAcceptedReservationAndInternalPendingTransfer` | 요청 ACCEPTED/RESERVED, 게시글 RESERVED, INTERNAL/PENDING 1건 동시 커밋 | PASS |
| P2-13 | 같은 메서드; Service `acceptsRequestAndCreatesTransferForSelectedBuyer` | 제품·요청·판매자·선택 구매자 일치, token/승인시각 NULL | PASS |
| P2-14 | PG `commitsAcceptedReservationAndInternalPendingTransfer`, `allowsOnlyOneConcurrentAcceptanceForSameListing` | 제품 owner 불변, 패자 PENDING 유지 | PASS |
| P2-15 | Entity `refusesToProcessFinishedRequestAgain`; Service `repeatedAcceptanceOfCompletedRequestHasNoSideEffects` | 종료 요청 재처리 TRADE_CONFLICT, update/reserve/transfer 없음 | PASS |
| P2-16 | Service `reservedRequestFromAnotherBuyerBlocksAcceptanceWithoutMutation`; PG `rollsBackAcceptanceWhenProductAlreadyHasPendingTransfer`; HTTP `returnsTradeConflictAndRollsBackWhenProductAlreadyHasPendingTransfer` | 기존 예약/이전 충돌은 409/TRADE_001, 요청 PENDING/NULL·게시글 ACTIVE·기존 이전 1건 유지 | PASS |
| P2-17 | PG `createsNewPendingRecordAfterCancellationAndPreservesHistory` | 기존 CANCELLED 보존, 다른 ID의 새 PENDING 생성 | PASS |
| P2-18 | PG `filtersSentAndReceivedRequestsByStatusRoomAndPage`; Service `listsSentRequestsWithFiltersAndListingStatuses`; HTTP 생성 시험 | SENT/RECEIVED·상태·방·페이지와 벌크 listing 상태 일치 | PASS |
| P2-19 | Service `returnsRoomContextForEmptyFilteredList` | 종료 게시글의 요청 없는 방도 빈 items와 자기 room/listing/status 최소 context 반환 | PASS |
| P2-20 | PG `concurrentCreatesReturnSamePendingRequest` | 독립 스레드·트랜잭션 응답 ID 동일, PENDING 한 행 | PASS |
| P2-21 | PG `allowsOnlyOneConcurrentAcceptanceForSameListing` | 성공 1·충돌 1, RESERVED·PENDING 이전 각 1, 패자 PENDING | PASS |
| P2-22 | PG `concurrentAcceptAndCancelLeaveOneConsistentResult` | 단일 성공 결과와 요청·게시글·이전 최종 상태 일치 | PASS |
| P2-23 | PG `rollsBackRequestAndListingWhenListingReservationFails` | 게시글 UPDATE 뒤 예외 시 요청 PENDING/NULL·게시글 ACTIVE·이전 0 | PASS |
| P2-24 | PG `rollsBackAllAcceptanceChangesWhenTransferInsertFails`; HTTP `rollsBackAcceptanceThroughSpringProxyWhenTransferInsertFails` | 비중복 트리거 장애는 500을 유지하며 이전 insert/실제 Spring 프록시 실패 모두 전체 롤백 | PASS |
| P2-25 | PG `databaseRejectsInvalidStatePairAndDuplicatePendingRoom`, `databaseRejectsDuplicateReservedListingAndMissingForeignKeys`, `databaseEnforcesEveryPurchaseRequestStatePairIncludingAcceptedNull` | 상태 CHECK, PENDING/RESERVED UNIQUE, FK, ACCEPTED/NULL INSERT·UPDATE 거부 | PASS |
| P2-26 | HTTP `reservationBlocksNewChatRoomButKeepsExistingRoomMessaging` | 예약 후 새 방 403, 기존 메시지 POST·GET, read-position, 실제 STOMP 구독 수신 성공 | PASS |
| P2-27 | Controller `rejectsMissingRoomId`, `rejectsRequestWithoutJwtAuthentication`; HTTP `rejectsInvalidAuthenticationInputAndPagingThroughRunningServer` | 인증 누락/무효 401, 입력·page·size 400, DB 변경 0 | PASS |
| P2-28 | `PurchaseRequestFeatureFlagTest` 3개 메서드; HTTP PostgreSQL 전체 기동 | 기본 off, Mock Service 제공 시 Controller 조건부 등록, 실제 테스트 경계로 on 기동, 경계 누락 on은 구성 실패 | PASS |
| P2-29 | 기본/보류 Flyway 위치 분리, 테스트 중 임의 스키마 생성·삭제 | fixture는 `src/test`, pending DDL 기본 경로 제외, 일반 실행 가짜 빈 없음 | PASS |
| P2-30 | `spotlessCheck`, 전체 `test`, `build` | 아래 최종 실행 결과 참조 | PASS |

## 실행 결과

### PostgreSQL 전용 거래 검사

- DB 제품: PostgreSQL 16 (`postgres:16-alpine` 임시 로컬 컨테이너)
- DB: 합성 데이터만 사용하는 `p2trade`
- 스키마: `p2_trade_<random>`, `p2_trade_http_<random>` 생성 후 각 클래스가 `DROP SCHEMA ... CASCADE`
- 컨테이너: Gradle 종료 후 제거 확인
- 명령: `P2_TRADE_TEST_ENABLED=true`와 전용 JDBC 환경변수를 설정한 뒤 `./gradlew test --tests 'org.example.springtestci.trade.*' --rerun-tasks --no-build-cache`
- 결과: 거래 테스트 48건 실행, 실패 0, 오류 0, skipped 0.

### 최종 전체 검사

- 명령: `./gradlew spotlessCheck test build --rerun-tasks --no-build-cache`
- 결과: 전체 테스트 330건, 실패 0, 오류 0, skipped 20. `spotlessCheck`, `test`, `build` 모두 성공했다.
- 일반 전체 검사에서는 외부 DB 환경변수가 없으므로 PostgreSQL 전용 클래스가 조건부 skipped 된다. 위 전용 실행으로 별도 0 skipped 결과를 확보했다.

## A/B 단계 구분

- A단계: 거래 코드, pending DDL, 격리 PostgreSQL·HTTP·STOMP 검증과 최신 `main` 통합까지 완료.
- B단계 대기: 정식 게시글/ownership 운영 구현 연결, pending Migration 정식 버전 승인과 공유 DB 적용, 쿠키/CSRF 최종 계약.
- 미포함: 실제 소유자 변경, P3~P5, UI, 배포와 PR 병합 승인.

## 재현 명령

일반 검사:

```powershell
.\gradlew.bat spotlessCheck test build --rerun-tasks --no-build-cache
```

PostgreSQL 전용 검사:

```powershell
$env:P2_TRADE_TEST_ENABLED='true'
$env:P2_TRADE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55432/p2_trade_test'
$env:P2_TRADE_TEST_DB_USER='p2_trade'
$env:P2_TRADE_TEST_DB_PASSWORD='<local-only password>'
.\gradlew.bat test --tests 'org.example.springtestci.trade.*' --rerun-tasks --no-build-cache
```

비밀번호와 서명 토큰 값은 저장소 문서나 실행 로그에 기록하지 않는다.
