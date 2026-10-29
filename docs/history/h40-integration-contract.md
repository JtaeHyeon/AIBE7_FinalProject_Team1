# H40 정식 history 접합부 연결 계약

## 기준과 범위

- 수용 기준: `017593f78359e2edfa75df11566e97a139c8e69f`
- 통합 기준: `upstream/main` `b21c42e96eb6745f95d8f9f764a3a99075d9c35a`
- 작업 시작 HEAD: `efe743c437a1be3045cd325a650fefdae1caa05f`
- 보존 항목: P3/P4 회귀 시험 보강, `V2026102801__create_product_history.sql`, pending 거래 DDL 격리
- 제외 항목: 정식 ownership 구현, P5, 화면, 공유 DB 이행, 외부 AI/S3 호출, 운영 활성화

열린 팀 PR `#53`은 실제 `product_events` 공개 reader와 카드 AI 요약을 작업 중이다. 이번 연결은 해당 PR을 합치거나 같은 reader를 중복 구현하지 않고 history 저장 측의 버전·P3·R10 경계만 다룬다.

## 호출 및 저장 계약

| 실제 호출자 | 연결 메소드 | 저장·전달 대상 | 트랜잭션·잠금 경계 | 기능 OFF |
|---|---|---|---|---|
| `HistoryController#create` | `HistoryService#create` | `product_events`, `event_documents`, 필요 시 `laptop_details`, `products.facts_version` | history 트랜잭션에서 제품 행을 먼저 `FOR UPDATE` | history 기본 등록은 유지 |
| `HistoryService#create` | 선택적 history→P3 어댑터 | RESERVED 요청의 `transaction_change_reviews`, 양측 확인 버전 NULL | 같은 트랜잭션에서 제품→RESERVED 판매글→구매 요청 순서로 잠금 | `trade-p3=false`이면 어댑터가 등록되지 않으며 가짜 성공 Bean을 두지 않음 |
| `HistoryService#create/correct/voidEvent` | `ProductFactsChanged` 발행 | 커밋 후 기존 R10 listener가 공개 판매글의 AI 최신화 작업 접수 | 업무 트랜잭션 안에서 발행, `AFTER_COMMIT` 처리. 롤백 시 미전달 | AI 접수 실패는 커밋된 history 쓰기를 되돌리지 않음 |
| 판매글 상세·AI·P4 | `ListingPublicationService`→`ListingHistoryReader` | 공개 projection | 기존 제품 잠금 안에서 읽기 | 정식 reader가 없으면 `UNAVAILABLE`; `READY` 고정 대역 금지 |

## 변경별 버전과 확인 처리

| 변경 | `facts_version` | P3 변경 확인 | 기존 완료 확인 |
|---|---:|---|---|
| 일반 이력 등록 | 정확히 1 증가 | RESERVED 거래가 있고 P3가 ON이면 새 안전 사본 1건 | 새 사본 저장과 같은 트랜잭션에서 양측 NULL |
| 사양 변경 등록 | 정확히 1 증가 | 변경 전후 사양을 포함한 안전 사본 1건 | 새 사본 저장과 같은 트랜잭션에서 양측 NULL |
| 이력 정정 | 정확히 1 증가 | 아래 식별 계약 확정 전 연결 보류 | 버전 불일치로 과거 확인은 완료에 사용 불가 |
| 이력 무효화 | 정확히 1 증가 | 아래 식별 계약 확정 전 연결 보류 | 버전 불일치로 과거 확인은 완료에 사용 불가 |

P3에 전달하는 생성 사본은 사건 유형, 발생일, 상태와 구조화된 사양 변경만 포함한다. 제목·본문·업체·부품·비용·문서 ID·작성자·감사 원문은 포함하지 않는다.

## 판매글·구매자·AI 공개 경계

- 판매글·구매자·AI에는 `ListingPublicationService`가 만든 공개 DTO만 제공한다. 소유자용 `HistoryEventResponse`를 재사용하지 않는다.
- P4 완료 근거는 정식 reader의 `READY`, `sample=false`, 현재 `facts_version`, 미확인 변경 0건이다.
- 공개 자유입력은 검토 완료 근거가 저장되어 있지 않으므로 기본 비노출한다. 유형·날짜·VALID/VOID·정정/무효 존재처럼 구조화된 정보만 제공하는 fail-closed 정책을 유지한다.
- 열린 PR `#53`이 같은 공개 reader 영역을 수정 중이므로 병합 전에는 이번 브랜치에서 reader를 중복 공급하지 않는다.

## 계약 확인 필요

### 동일 이력의 정정·무효 식별

현재 pending P3 DDL의 기본 키 `(purchase_request_id, event_id)`와 API의 `eventId` 식별만으로는 동일 이력의 생성 사본을 보존하면서 정정·무효 사본을 새 확인 대상으로 추가할 수 없다.

최소 설계안은 별도 `change_id`를 결정 식별자로 추가하고 `(purchase_request_id, event_id, facts_version)`을 유일하게 유지하는 것이다. 응답은 `changeId`, 원본 `eventId`, `factsVersion`을 함께 제공하고 KEEP/CANCEL은 `changeId`에 기록한다. 이 변경은 pending DDL·DTO·API 계약 변경 승인을 받은 뒤 구현한다.

### 공개 자유입력 검토

현재 스키마에는 제목·본문·업체·부품·정정 사유의 공개 검토자와 검토 시각이 없다. 별도 합의 전 최소 정책은 모든 자유입력을 `REVIEW_PENDING`으로 처리하고 공개하지 않는 것이다. 공개 허용에는 필드별 검토 상태와 감사 근거가 필요하며 단순 정규식 통과를 검토 완료로 간주하지 않는다.
