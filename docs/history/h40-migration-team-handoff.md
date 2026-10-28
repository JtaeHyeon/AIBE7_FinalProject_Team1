# H40 Migration·회귀 팀원 인계

## 검토 대상과 병합 전 결정

**로컬 신규/demo 경로는 검증됐다. 옛 일반 ownership이 이미 적용된 DB는 이 후보로 그대로 업그레이드할 수 없다.** 실제 지원 환경의 적용 이력이 미확인이므로 채택 여부는 병합 전 결정한다. 이 문서는 팀 DB에 접속하거나 이행하도록 승인하지 않는다.

- 고정 B: `f5ad1025abb86a79cb8ff35d7178c8e80aade151`.
- 코드·시험 C: `7b701c017c3711b7003bd58735c8e00994ee5184`.
- 이 PR의 R은 C에 결과·인계 문서를 추가한 head이며 정확한 SHA는 PR에서 확인한다.
- 이번 보정은 기존 H40 운영 연결·R1·경합 보정·통합 호환성 및 P2/P3/P4 시험을 보존한다. 상세 실행은 [현재 결과](h40-migration-local-validation.md)를 기준으로 한다. 과거 `28ad18f`의 BLOCKED는 조사 당시 기록이다.

## 파일 대응

| 파일 | 변경 |
|---|---|
| 일반 ownership SQL | `V2026102702` → `V2026102702_1`. 본문 동일, Flyway version `2026102702.1` |
| demo 카드 요약 `V2026102702` | 변경 없음. 기존 script/checksum·구버전 식별자 보존 |
| ListingCardSummaryMigrationTest | legacy target 유지, 당시 컬럼으로 데이터 준비, 후속 backfill·Flyway 이력 보존 assertion 보강 |
| H40MigrationCompatibilityTest / 역사 SQL fixture | 신규 일반+demo 적용과 옛 일반 DB 비호환 진단. 역사 SQL 출처는 B의 옛 일반 ownership 파일 |
| H40 운영 Java·대역·설정·pending·후속 SQL | 이번 보정에서 변경 없음 |

## 어떤 환경에 적용할 수 있는가

| 상태 | 조건 | 확인한 DB / 결과 |
|---|---|---|
| VERIFIED | 새 일반 DB 또는 일반+pending 순차 적용 | 전용 PostgreSQL, 실제 프로젝트 Migration |
| VERIFIED | 새 일반+demo | H2·전용 PostgreSQL, 두 ownership/demo 버전 공존 |
| VERIFIED | 기존 demo `2026102702__version_listing_card_summary` 이력이며 옛 일반 ownership은 미적용 | H2·전용 PostgreSQL 합성 재현. 기존 요약·제목·이력/checksum 및 시간 컬럼 backfill 보존 |
| INCOMPATIBLE | 옛 일반 `2026102702__create_ownership_tables`가 이미 적용됨 | B 일반을 최신 `2802`까지 정상 적용한 H2·전용 PostgreSQL에서 후보 validate/migrate 거부. 데이터/스키마/이력 무변경 |
| NOT_TESTED | 실제 팀 보존/공유/운영 환경 및 위 재현 조건 밖의 개별 상태 | 접속하지 않았음. 미적용으로 추정하지 않음 |

옛 일반 진단 시험의 PASS는 예상 비호환을 정확히 탐지한 **HARNESS_PASS / UPGRADE_INCOMPATIBLE**다. 실제 업그레이드 성공이 아니다. version `2026102702`라는 숫자만으로 환경을 분류하지 말고 script와 checksum을 함께 확인한다. 실제 demo 재현 checksum은 `1842460411`, 옛/새 ownership SQL 재현 checksum은 `-1318655273`이다. 팀 환경의 값이라고 가정하지 않는다.

## 재현 방법

전용 DB만 사용하고 비민감 합성 데이터로 재현한다. JDK 17·Gradle wrapper를 사용한다. `test`는 build.gradle에서 .env를 읽지 않게 구성되어 있다. 일반/demo locations와 기존 legacy target을 바꾸지 않는다.

```powershell
# H2: 기존 실패 사례 + 신규/demo 및 옛 일반 비호환 진단
.\gradlew.bat clean test --tests '*ListingCardSummaryMigrationTest' --tests '*ListingDemoUpgradeTest' --tests '*H40MigrationCompatibilityTest' --rerun-tasks --no-daemon
# 전용 PG를 SALE_DRAFT_TEST_DB_URL/USER/PASSWORD에 지정
# URL은 시험 안전 가드에 맞는 전용 sale_ai_test DB여야 함
.\gradlew.bat test --tests '*ListingCardSummaryMigrationTest' --tests '*H40MigrationCompatibilityTest' --rerun-tasks --no-daemon
# opt-in/SALE_DRAFT 연결과 실제 외부 키를 제거한 기본 자식 셸
.\gradlew.bat spotlessCheck classes testClasses test build --rerun-tasks --no-daemon
```

기존 8개 PG/Redis 영향 묶음의 정확한 명령과 환경변수는 결과 문서에 있다. 일반 Migration의 개명이므로 이 8개 묶음도 이번 C에서 재실행해 109/109 PASS했다. 기본은 759건 중 704 PASS/55 SKIPPED, failures/errors 0이며 build 성공이다. 기본 skip 54건은 선택의 동일 testcase PASS로 대응하고 Redis 단위시험 1건은 별도 NOT_RUN이다. 모든 실행 수치를 중복 합산하지 않는다.

역사 준비는 실제 B SQL/Java를 정상 migrate한다. fixture의 출처는 `src/test/resources/db/fixtures/h40-legacy-general/README.md`에 있다. 이후 후보의 실제 classpath locations로 validate/migrate하며 schema history 직접 수정·repair/clean·검증 해제를 사용하지 않는다. 진단은 `FlywayValidateException`의 옛 version과 unresolved 메시지만 허용하고 전후 데이터·컬럼·이력 동일성을 검사한다.

## 팀원이 확인할 정보와 후속 변경 처리

1. 팀이 보존·지원하는 환경 목록과 각 환경의 실제 Flyway locations를 확인한다. 무관한 모든 개인 PC의 부재 증명이 필요한 것은 아니다.
2. ownership `2026102702`·후속 `2026102707` 및 demo `2026102702`의 실제 적용 여부를 비민감 version/script/checksum/success로 분류한다. 계정·토큰·DB dump를 공유하지 않는다.
3. 옛 일반 적용 환경이 지원 범위라면 이 후보의 비호환을 해소하는 별도 호환성 대안 또는 지원 범위 결정을 병합 전에 확정한다. 이번 PR은 보존 DB용 repair/clean/이력 UPDATE를 해결책으로 제안하지 않는다.
4. 후속 팀 PR/응답이 오면 해당 파일·version·본문과 C의 diff를 대조하고 중복 구현을 제거한 뒤 영향 검사를 갱신한다. 자동으로 최신 main 전체를 합치거나 담당자·기한·합의를 만들어 넣지 않는다.
5. 실제 PR base가 B보다 전진했는지와 정확한 head/merge ref의 CI 결과를 병합 직전에 다시 확인한다. 로컬 통과와 원격 CI·병합 승인은 별개다.

정식 ownership 취소·완료 내부 포트, ACCEPTED/COMPLETED 업무 매핑, 요청/이전 ID·토큰·만료·잠금·완료 원자성 부수효과, H40-06·R07·P5·화면·공유 DB 이행·운영 활성화는 이번 PR의 완료 주장에 포함하지 않는다. 거래 기능 기본 OFF와 pending 격리를 유지한다. 원본 로그/XML은 사용자에게 전달한 검수 ZIP에 있고, 실제 확보하지 않은 원격 증거 링크나 사용자 PC 경로를 공유 자료처럼 붙이지 않는다.
