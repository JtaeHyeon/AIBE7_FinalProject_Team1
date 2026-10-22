# P2 Stage B Migration 계획

## 현재 상태

- 거래 DDL은 `db/pending/trade-p2/V2026102103__create_purchase_requests.sql`에 유지한다.
- 일반 Flyway 경로의 최신 파일은 `V2026102101__create_published_listings.sql`이며 Java Migration `V2026102102__isolate_published_listing_ids`가 뒤따른다.
- 격리 PostgreSQL에서는 기본 Migration 적용 후 pending 위치를 추가해 거래 DDL의 FK, 상태 조합, PENDING 방 유일성, RESERVED 판매글 유일성을 검증했다.
- 정식 `ownership_transfers` Migration은 제공되지 않았다.

## 편입 전 필수 확인

1. 공유 개발·운영 DB의 `flyway_schema_history`에서 동일 버전 또는 수동 DDL 적용 이력을 읽기 전용으로 확인한다.
2. `users`, `products`, `listings`, `chat_rooms`의 PK 타입과 기존 데이터 FK 위반 건수를 확인한다.
3. 기존 채팅방의 `listing_id`가 정식 판매글을 가리키는지 확인하고, 초안 ID 흔적은 자동 보정하지 않는다.
4. ownership Migration이 `purchase_requests`를 참조하는지 확인해 최종 실행 순서를 확정한다.
5. 충돌 없는 새 버전으로 pending 파일을 일반 경로에 한 번만 편입하고 fresh·upgrade 시험을 다시 수행한다.

## 보류 사유

공유 DB 접근·적용 승인이 없고 ownership DDL의 의존 방향도 확정되지 않았다. 현재 파일을 이동하면 애플리케이션 기동만으로 모든 환경에 DDL이 적용되므로 이번 변경에서는 정식 경로로 편입하지 않는다. `IF NOT EXISTS`, `repair`, `baseline`, `clean` 또는 기존 Migration 수정으로 차이를 숨기지 않는다.

## 복구 원칙

공유 환경 적용 전 백업과 영향 건수를 승인받는다. 적용 실패 시 임의 `clean`을 수행하지 않고, 적용된 버전과 DDL 상태를 보존한 채 승인된 후속 Migration 또는 DB 복구 절차를 사용한다.
