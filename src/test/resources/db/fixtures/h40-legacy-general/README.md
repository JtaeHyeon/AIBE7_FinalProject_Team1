# H40 옛 일반 DB 준비 원천

`V2026102702__create_ownership_tables.sql`은 고정 B
`f5ad1025abb86a79cb8ff35d7178c8e80aade151`의
`src/main/resources/db/migration/V2026102702__create_ownership_tables.sql` 원문이다.
현재 운영 Flyway locations에는 이 fixture 경로를 넣지 않는다.

`H40MigrationCompatibilityTest`는 이번 후보의 일반 SQL 중 새 ownership 파일을 제외한
동일한 B 원문과 이 옛 파일, B와 같은 Java Migration을 임시 역사 준비 경로에 모아
B 일반 DB를 정상 migrate한다. 이후 후보의 실제 일반 classpath 경로로 validate/migrate하여
단순 개명안의 비호환을 검사한다. 현재 후보를 적용하는 경로에서는 파일을 제외하지 않는다.
schema history 직접 삽입/수정이나 repair/clean은 사용하지 않는다.
