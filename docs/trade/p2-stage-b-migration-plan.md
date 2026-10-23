# P2 Stage B Migration 계획

## 현재 상태

- `V2026102301__create_purchase_requests.sql`이 거래 요청 테이블과 상태 조합 제약을 생성한다.
- `V2026102302__create_ownership_transfers.sql`이 내부·외부 이전 공통 테이블, FK, 유형·상태·형태 제약을 생성한다.
- `V2026102303__add_trade_pending_unique_indexes`가 PostgreSQL에 PENDING 방, RESERVED 판매글, 제품별 PENDING 이전 부분 고유 인덱스를 생성한다.
- 이전 pending DDL과 테스트용 ownership 테이블 생성 코드는 제거했다.
- 격리 PostgreSQL 17에서 fresh, `V2026102002` 기준 upgrade, validate, 재실행을 검증했다.

## 확정된 실행 순서

1. 기존 사용자·채팅·제품·판매글 Migration 적용
2. `V2026102102`에서 legacy 채팅 번호와 새 판매글 identity 구간 분리
3. `V2026102301`에서 `purchase_requests` 생성
4. `V2026102302`에서 `purchase_requests`를 참조하는 `ownership_transfers` 생성
5. `V2026102303`에서 PostgreSQL 부분 고유 인덱스 생성

H2는 로컬 기본 기능이 비활성화된 환경의 스키마 기동 호환성을 위해 테이블·FK·CHECK·일반 UNIQUE까지 적용하고, PostgreSQL 전용 부분 고유 인덱스는 건너뛴다. 거래 기능의 동시성·부분 고유 제약은 PostgreSQL 전용 시험에서 검증한다.

## 검증 결과

- fresh: 전체 일반 Migration 적용, 거래·ownership 테이블과 부분 고유 인덱스 3개 확인
- upgrade: `V2026102002`까지 적용 후 legacy `chat_rooms.listing_id=77`을 삽입하고 최신까지 적용
- legacy 격리: 기존 채팅 번호 77은 유지되고 새 판매글 ID는 77보다 큰 값으로 발급됨을 확인
- validate·재실행: checksum 검증 통과, 최신 상태 재실행 시 적용 Migration 0건 확인
- production Writer: 수락 성공, 제품별 PENDING 충돌, DB 실패 및 예약 후 실패의 전체 롤백 확인

## 공유 환경 적용 전 확인

1. `flyway_schema_history`에서 `2026102301`~`2026102303`과 수동 거래 DDL 적용 이력이 없는지 읽기 전용으로 확인한다.
2. `users`, `products`, `listings`, `chat_rooms`의 PK 타입과 거래 FK 위반 후보를 집계한다.
3. 기존 `chat_rooms.listing_id`를 정식 `listings.id`와 대조해 우연한 숫자 일치와 초안 ID 흔적을 구분한다.
4. 백업·적용 시간·영향 범위를 승인받은 뒤 Migration을 적용한다.
5. 승인된 환경에서 구매 요청 생성·수락·ownership PENDING 생성·채팅 유지 smoke를 수행한다.

수동 DDL 흔적이 있으면 `IF NOT EXISTS`, `repair`, `baseline` 또는 기존 Migration 수정으로 숨기지 않는다. 적용된 객체와 이력을 보존한 채 별도 정합화 계획을 승인받는다.

## 복구 원칙

공유 환경 적용 전 백업과 영향 건수를 승인받는다. 적용 실패 시 `clean`을 수행하지 않고, 적용 버전과 DDL 상태를 보존한 채 승인된 후속 Migration 또는 DB 복구 절차를 사용한다.
