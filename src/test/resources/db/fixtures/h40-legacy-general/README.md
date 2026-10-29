# H40 옛 일반 DB 준비 원천

`V2026102702__create_ownership_tables.sql`은 고정 B
`f5ad1025abb86a79cb8ff35d7178c8e80aade151`의
`src/main/resources/db/migration/V2026102702__create_ownership_tables.sql` 원문이다.
현재 운영 Flyway locations에는 이 fixture 경로를 넣지 않는다.

`V2026102707__add_ownership_pending_slot.sql`도 같은 B의 원문이다.
두 파일은 시험 전용이며 SQL 본문을 수정하지 않는다.

`H40MigrationCompatibilityTest`는 현재 정식 번호 `2803`·`2804`의 신규 적용과
반복 검증에서 이력·데이터·제약 보존 및 생성 SQL의 미재실행을 확인한다.
고정 ownership 원문은 시험 준비 경로에서 현재 `2803` 파일명으로 사용한다.

별도 음성 사례는 두 고정 파일을 예전 `2702`·`2707` 이름 그대로 적용한다.
그 DB에는 현재 migration의 validate/migrate가 실패하고 이력·제품·이전 행·제약이
그대로 남아야 한다. 이는 #57의 구 소유권 버전 미지원·별도 검토 정책을 확인한다.
시험 과정에서 schema history 직접 삽입/수정이나 repair/clean은 사용하지 않는다.
