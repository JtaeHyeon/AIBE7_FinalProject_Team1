# H40 정식 history 접합부 연결 결과

## 실행 기준

| 항목 | 결과 |
|---|---|
| 작업 경로 | `C:\Workspace\AIBE7_FinalProject_Team1-h40-20261028` |
| 수용 기준 | `017593f78359e2edfa75df11566e97a139c8e69f` |
| 최신 팀 main | `b21c42e96eb6745f95d8f9f764a3a99075d9c35a` |
| 통합 시작 HEAD | `efe743c437a1be3045cd325a650fefdae1caa05f` |
| 구현 커밋 | `5671c994d82ccfa64881d47219fc8c9d97b97477` |
| 시험 커밋 | `155b4ea6466904567f1e916be4c6b83b7439523b` |
| JDK / DB / Redis | Temurin 17.0.19 / PostgreSQL 17.11 / Redis 8.10.3 |
| 기능 플래그 | `trade-purchase-requests`, `trade-p3`, `trade-p4` 기본 OFF 유지 |

최신 main은 작업 종료 전 fetch에서도 위 SHA로 유지됐다. 열린 팀 PR `#53`은 미병합 상태이며 실제 공개 history reader와 카드 AI 요약 영역이 겹치므로 이번 브랜치에 합치거나 중복 구현하지 않았다.

`V2026102801__create_product_history.sql`과 수용된 P3/P4 시험 보강을 그대로 보존했다. 일반 Flyway Migration과 `db/pending/trade-p2`, `db/pending/trade-p3`의 격리를 유지했고 SQL 파일이나 버전을 새로 변경하지 않았다.

## 구현 결과

- `HistoryService#create`는 일반 이력과 사양 변경 모두 `products.facts_version`을 정확히 한 번 증가시킨다.
- P3가 ON이면 선택적 `HistoryChangeReviewRecorder` 운영 구현이 제품→RESERVED 판매글→구매 요청 순서로 잠그고, 실제 `ReservationChangeService#recordChange`에 안전 사본을 같은 트랜잭션으로 전달한다.
- 안전 사본은 사건 유형·발생일·상태·구조화된 사양만 포함한다. 제목·본문·업체·부품·비용·문서 ID·작성자·감사 원문은 포함하지 않는다.
- P3 저장은 새 review와 양측 완료 확인 무효화를 같은 트랜잭션에 포함한다. 연결 저장 실패 시 이력·사양·버전·review·확인이 함께 롤백된다.
- 등록·정정·무효는 기존 `ProductFactsChanged`를 발행한다. 기존 R10 listener가 커밋 후 최신화 작업을 접수하며 롤백된 변경은 전달하지 않는다.
- P3 OFF에는 recorder Bean이 없고 history CRUD는 정상 동작한다. `READY` 또는 성공을 고정하는 대체 운영 Bean은 추가하지 않았다.

## H40 판정

| ID | 구현 | 실행 | 연결 수준 | 결과·잔여 |
|---|---|---|---|---|
| H40-01 | CODE_READY | PASS | 정식 history + 실제 PostgreSQL | 일반/사양 등록·정정·무효가 각각 버전을 한 번 증가시킴 |
| H40-02 | CODE_READY | PASS | `HistoryService#create`→정식 P3 서비스/저장소 | 안전 사본 저장과 양측 확인 NULL 확인 |
| H40-03 | CODE_READY | PASS | 동일 Spring 트랜잭션 | 연결 실패 전체 롤백 및 완료와 history 등록 경합의 단일 결과 확인 |
| H40-04 | CODE_READY | PASS | `ProductFactsChanged`→기존 R10 | 실제 create 커밋 후 listener 접수, 전체 생명주기 이벤트 발행, 롤백 시 미접수 확인. 외부 AI 호출 없음 |
| H40-05 | CODE_TODO | NOT_RUN | DEPENDENCY_BLOCKED / CONTRACT_REVIEW | P3 사본의 비공개 필드 제외는 PASS. 실제 공개 reader는 열린 PR `#53`과 중복되어 미연결 |
| H40-06 | CODE_TODO | NOT_RUN | CONTRACT_REVIEW | 정정·무효를 새 review로 식별할 스키마/API 합의 필요 |
| H40-07 | CODE_READY | PASS | 기능 플래그 OFF | 기본 회귀 584 PASS 및 P3 OFF 실제 history 생명주기 PASS |

## 계약 확인 필요

현재 pending P3 테이블은 `(purchase_request_id, event_id)`가 기본 키이고 결정 API도 `eventId`를 사용한다. 기존 생성 사본을 불변으로 보존하면서 동일 이력의 정정·무효를 새 확인 대상으로 추가할 수 없다.

최소안은 별도 `change_id`를 결정 식별자로 추가하고 `(purchase_request_id, event_id, facts_version)`을 유일하게 두는 것이다. 응답에는 `changeId`, 원본 `eventId`, `factsVersion`을 함께 제공한다. 이는 pending DDL·DTO·API 변경이므로 이번 작업에서 임의 확정하지 않았다.

자유입력 공개 검토자·검토 시각도 현 스키마에 없다. 별도 합의 전에는 유형·날짜·상태·정정/무효 존재만 공개하고 모든 자유입력을 `REVIEW_PENDING`으로 비노출하는 fail-closed 기준을 적용해야 한다. 정규식 통과만으로 검토 완료를 판정하지 않는다.

## 검증 결과

### 외부 자원 없는 기본 회귀

```text
git diff --check
gradlew.bat spotlessCheck classes testClasses test --rerun-tasks --no-daemon
```

| tests | PASS | failures | errors | SKIPPED |
|---:|---:|---:|---:|---:|
| 634 | 584 | 0 | 0 | 50 |

SKIPPED 50건은 opt-in PostgreSQL P2 15건, P2 HTTP/STOMP 7건, P3 16건, P4 11건과 Redis 단위 시험 1건이다. 외부 자원 없이 실행되는 기본 시험은 전부 통과했다.

### 전용 PostgreSQL·Redis 선택 회귀

폐기 가능한 루프백 컨테이너와 tmpfs만 사용했다. 실제 외부 AI/S3, 공유 DB, 보존 DB에는 접근하지 않았다.

| 실행 | tests | PASS | failures | errors | SKIPPED |
|---|---:|---:|---:|---:|---:|
| P2 repository | 15 | 15 | 0 | 0 | 0 |
| P2 HTTP/STOMP | 7 | 7 | 0 | 0 | 0 |
| P3 PostgreSQL | 16 | 16 | 0 | 0 | 0 |
| P4 PostgreSQL | 11 | 11 | 0 | 0 | 0 |
| 합계 | 49 | 49 | 0 | 0 | 0 |

신규 H40 실제 연결 시험 5건은 다음을 검증한다.

1. 실제 create→버전 증가→P3 사본→양측 확인 무효화→커밋 후 R10 접수.
2. P3 연결 실패 시 history·버전·review·확인 전체 롤백과 R10 미접수.
3. P3 OFF에서 사양 등록·일반 등록·정정·무효의 단일 버전 증가와 이벤트 발행.
4. 실제 history 등록 뒤 P4 완료 차단.
5. 완료와 실제 history 등록 경합의 COMPLETED 또는 RESERVED 단일 결과.

첫 실행에서는 Java 8이 선택되어 Gradle 시작이 중단됐고 JDK 17을 명시해 해결했다. PostgreSQL 준비 중 `55432` 포트 선점과 시험 SQL의 JSONB `?` 연산자 바인딩 오인, 최소 이벤트 컨텍스트의 transactional listener factory 누락을 각각 포트 변경·`jsonb_exists`·실제 listener factory 등록으로 보정했다. 최종 결과에는 성공한 기본 회귀와 49건 선택 회귀만 합산했다.

증거는 커밋하지 않는 `build/h40-evidence` 아래 최종 Gradle 로그와 실행별 JUnit XML로 보존했다. 로그에서 자격 증명·토큰·쿠키·DB 덤프는 확인되지 않았다. 전용 PostgreSQL·Redis 컨테이너는 실행 후 제거했고 사용 포트의 LISTEN 해제를 확인했다.

## 이월

- 열린 PR `#53` 또는 후속 main 기준 실제 공개 reader·구매자/AI projection 통합과 공개 자유입력 검토 근거.
- 정정·무효의 새 review 식별 계약과 승인된 pending DDL·DTO·API 변경.
- 정식 ownership pending/cancellation/completion 구현과 전체 동일 트랜잭션 인수.
- R07 판매 중지 사유 계약, 공유/보존 DB 이행·checksum, 화면 인수, 운영 활성화, 배포, P5.

push, PR, merge, 공유 DB Migration, 운영 활성화는 수행하지 않았다.
