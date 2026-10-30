# H40 Migration 충돌 후속 조사 결과

## 현재 판정과 기준

**H40 제출 준비: BLOCKED / APPLICATION_HISTORY.** 일반 ownership Migration을 이동해도 되는 보존 환경 적용 이력·담당 범위가 확보되지 않아 Migration을 변경하지 않았다. 알려진 일반/demo 충돌은 남아 있으며 F-02/F-03의 이전 FAIL을 해소했다고 판정하지 않는다. 정식 거래·이전 전체 인수는 별도 **BLOCKED / CONTRACT_REVIEW**다.

- S: `4d17244608b20bcc9877afdb6cf4c130774d1410`.
- C: 미생성. 이번 수정·시험 코드 변경은 없고 실행 가능 소스는 기존 T1 `50c9632948bf3ea9ec0b59eb1a36d4a08bac0563`과 동일하다.
- R: 이 문서와 PR 초안만 포함한 보고 커밋. 전체 SHA는 검수 ZIP `MANIFEST.json`에 기록한다.
- B / U: 고정 `f5ad1025abb86a79cb8ff35d7178c8e80aade151` / 2026-10-28 KST 읽기 조회에서도 동일.
- worktree / branch: `C:\Workspace\AIBE7_FinalProject_Team1-h40-final-20261028` / `test/h40-final-gate0-20261028`.
- 기존 T0 `22489cac15b1539a3331c8569e03f58a8195f5f7`, H40 수용본 `003058e4f764ae30bef24833bc93e4f76f768684`와 T1을 보존했다. 시작 시 clean, merge/rebase 진행 없음이다.
- 기준 ZIP: `review-h40-final-4d17244.zip`, SHA-256 `88e4db25a197a6848e327e62dedd7e7744194f2f369aed488db47f58c9bbe771`을 대조했다.
- 도구/환경: Windows PowerShell·Git·공개 GitHub API 읽기. 모델은 세션에 GPT-6 기반 Codex로 설명되어 있으나 정확한 런타임 ID·effort는 미확인이다. 이번에 JDK/Gradle/DB/Redis 실행은 하지 않았다.

명령서 §5.2의 적용 이력 미확인 분기를 따른 결과다. 새로 허용한 보정 권한 자체를 미적용 증거로 사용하지 않았다. 소스가 같아 실패를 다시 확인하기 위한 전체 시험은 반복하지 않았고, 독립적인 기준·locations·의존·원격 선행 변경 조사와 수정/검증 계획을 완료했다.

## 적용 이력 확인 범위

| 확인 항목 | 일반 ownership `V2026102702` | 기존 demo `V2026102702` |
|---|---|---|
| 팀이 보존·지원하는 환경 목록 | 미확인. local/prod/test 구성 존재는 확인했으나 실제 지원 DB 목록·보존 필요 여부와 같지 않음 | `AI_LISTING_CARD_SUMMARY.md` 및 부모 PR #53이 기존 로컬 시연 DB 이력 보존을 명시. 구체적인 보존 인스턴스 목록은 미확인 |
| 소스의 실제 locations | 일반 기본은 `classpath:db/migration`; 전용 거래 시험은 일반+pending을 순차 적용 | `application-sale-draft-demo.yaml`과 두 실패 시험은 `classpath:db/migration,classpath:db/demo/sale-draft` |
| 보존 환경의 적용 여부 | 미확인. 담당자의 미적용 확인이나 schema history 발췌 없음 | 문서상 기존 시연 적용 사실 있음. 이 사실을 보존하며 변경 대상에서 제외 |
| 적용 script/version/checksum/success | 보존 환경 값 전부 미확인. 파일명·Git blob은 별도 inventory에 기록 | 문서상 script/version은 `V2026102702__version_listing_card_summary.sql` / `2026102702`. 실제 보존 DB checksum/success 발췌는 미확인 |
| 후속 의존·적용 상태 | `V2026102707__add_ownership_pending_slot.sql`이 ownership 테이블을 ALTER. 소스 의존 확인, 보존 환경 적용 상태 미확인 | `V2026102802__version_listing_card_summary.sql`이 기존 컬럼·값을 보존하며 제약을 정렬. `V2026102706` 요약 갱신 컬럼과도 업그레이드 순서 관련. 보존 환경 적용 상태 미확인 |
| 근거·담당 범위 | PR #52 head `f29163f130dd1231837d4581840d453bb3ebd81b`, 파일 이력 `d214fc3`, source 구성. 적용 이력에 대한 담당자 진술 없음 | PR #53 head `3d9cc474d0dbc2746dc406aa5b483462e1ef9ada` 및 카드 요약 문서. PR은 기존 demo 보존과 이번 실제 개발/demo DB Migration 미수행을 명시하지만 ownership 미적용까지 보증하지 않음 |

현재 실제 환경 roster를 확정할 수 없으므로 무관한 개인 PC 전체의 부재 증명을 요구하지 않는다. 필요한 확인은 **팀이 보존·지원하기로 한 관련 환경**에 한정한다. 과거 T1의 폐기 가능한 PostgreSQL에 일반 Migration이 적용된 것은 시험 기록이며, 그 컨테이너는 정리됐다. 이 기록으로 보존 환경 미적용을 증명하거나 현재 DB 이력을 추정하지 않았다.

2026-10-28 약 20:30~20:32 KST에 부모 PR #52/#53의 본문·사람이 작성한 issue/review comment와 열린 PR을 읽기 조회했다. 해당 두 PR의 적용 이력을 보완하는 사람의 댓글은 없었고 열린 PR 목록은 0건이었다. 관련 두 PR와 조회 당시 열린 PR 범위에서 선행 해결안이 확인되지 않았다는 뜻이며, 모든 외부 브랜치·비공개 협의를 조사했다는 뜻은 아니다. Bot 본문·외부 서비스 링크는 적용 이력 증거로 쓰지 않았다.

공유·운영·팀원 보존 DB에 접속하지 않았고 .env·계정·비밀값도 조회하지 않았다. 필요한 자료를 사용자에게 비민감 항목으로 요청했다.

## locations별 조사와 조건부 최소안

`migration-inventory-before.csv`는 일반 SQL 16개와 Java Migration 1개, P2 pending 1개, P3 pending 1개, demo 4개를 수록한다. 파일 SHA-256·Git blob은 소스 무결성 값이며 실제 Flyway checksum과 구분한다.

| 실제 구성 | 파일 수 | 중복 |
|---|---:|---|
| 일반 | 17 | 없음 |
| 일반+P2 pending | 18 | 없음 |
| 일반+P2+P3 pending | 19 | 없음 |
| 일반+demo | 21 | `2026102702` 1개 버전, 두 파일 충돌 |

일반+demo는 실제 demo 프로필과 시험에서 사용하는 조합이다. 임의의 demo+pending 조합을 새 필수 대상으로 만들지 않았다.

**미적용 확인을 받은 경우에만 채택할 수 있는 최소 후보**는 일반 ownership 파일을 `V2026102702_1__create_ownership_tables.sql`로 이동하는 것이다. 이는 검토안이며 이번에 버전을 확정하거나 파일을 이동하지 않았다. Flyway 수치 순서상 기존 demo `2026102702` 뒤이며 ownership 후속 `2026102707` 앞에 위치하고, 현재 조사 목록에서 `2026102702_1`은 비어 있다. 새 변경 시점에는 다시 예약·충돌 여부를 확인해야 한다.

| 파일/범위 | 제안 | 근거와 제한 |
|---|---|---|
| 일반 ownership | 조건 충족 시 `2026102702` → `2026102702_1` 개명만 | SQL 본문 Git blob을 그대로 유지. 일반+demo 중복을 제거하고 `V2026102707`의 테이블 의존 순서 보존 |
| 기존 demo `V2026102702` | version/script/본문 유지 | 명시된 과거 적용 이력·구버전 fixture 보존 |
| 일반 `V2026102706`, `V2026102707`, `V2026102801`, `V2026102802` 및 pending | 그대로 유지 | 이번 단일 충돌과 무관한 재개명·정책 변경 제외 |
| 현재 파일명 직접 참조 | 실제 현재 적용 참조만 갱신 | 기존 결과·과거 적용 기록의 옛 경로를 새 경로로 덮어쓰지 않음 |

동시에 정적 조사에서 **후속 시험 fixture의 순서 문제 가능성**도 확인했다. `ListingCardSummaryMigrationTest`의 legacyDemo 준비는 target `2026102702`인데 그 직후 INSERT가 `V2026102706`에서 생기는 `summary_requested_at`·`summary_available_at`을 사용한다. resolver 중복이 제거되면 이 INSERT가 존재하지 않는 컬럼을 참조할 수 있다. 이는 아직 실행 재현하지 않은 정적 예측이며 새 운영 결함 판정으로 확장하지 않는다.

후속 검증에서는 구버전 target을 최신 버전으로 올리거나 준비 단계를 삭제하지 말고, 실제 구버전 컬럼만 사용하는 legacy INSERT와 이후 `V2026102706`의 시간 컬럼 backfill을 함께 확인하는 최소 fixture 보완을 검토한다. demo 이력의 version/script/checksum/success를 업그레이드 전후 명시적으로 대조하고 기존 요약·제목·제약 assertion을 보존한다. 이 보완도 조건부 검토안이며 이번에는 시험 코드를 바꾸지 않았다.

배제한 대안은 기존 demo 개명/본문 변경, 적용 이력 UPDATE/repair/clean, locations에서 충돌 파일 제외, 기존 시험 삭제·준비 target 최신화, CHECK/NOT NULL/FK/slot 완화다. 이미 적용된 일반 ownership 파일을 이동해야 한다면 위 단순 개명안은 사용할 수 없으며 담당자의 환경별 호환성 계획이 먼저 필요하다. 후속 SQL 하나만 추가해 resolver 중복이 해결됐다고 처리하지 않는다.

## 실행 상태 및 보존

| ID | 현재 상태 | 근거 / 이전 수용 근거 유지 |
|---|---|---|
| M-01 | BLOCKED | 소스 inventory·의존 확인 완료, 이동 대상의 보존 환경 적용 이력·담당 결정 미확인 |
| M-02 | NOT_RUN | 이번 수정 없음. 기존 두 클래스·3 실패의 XML/로그를 prior-t1로 보존, 새 실행과 구분 |
| M-03 | NOT_RUN | 수정 조합을 확정하지 않아 신규 적용·지원 업그레이드 재실행 없음 |
| M-04 | NOT_RUN | C 미생성, 기본 전체 시험/build 재실행 없음. 문서에 대한 `git diff --check`는 정적 검사 |
| M-05 | NOT_RUN | 일반 Migration을 변경한다면 8개 선택 묶음 전부 C에서 재실행할 계획. T1 109 PASS를 이번 PASS로 복사하지 않음 |
| M-06 | PASS(정적) | S/T1 대비 운영·시험·설정·SQL 변경 0, R1/T1·기본 OFF·대역 경계 보존. 이번 신규 자원 0 |
| F-02 / F-03 | 이전 FAIL 유지 | T1 전체 757건 중 699 PASS / 3 FAIL / 55 SKIPPED와 실제 일반/demo 충돌 근거 유지. 이번 NOT_RUN과 구분 |

이번 연결 수준은 소스/기록 정적 확인이며 UNIT/DB_PARTIAL/REAL_MODULE/HTTP의 새 실행은 없다. 이전 선택 109 PASS는 그 중 일부가 DB_PARTIAL이며 정식 ownership 전체 인수 완료가 아니다. 이전 기본 SKIPPED 55개 중 54개가 같은 testcase의 선택 PASS에 대응했고 Redis 단위시험 1건은 NOT_RUN이었던 기록도 유지한다. 이번 새 실행은 없으므로 집계는 **N/A**, 성공 0건 또는 SKIPPED 0건의 통과로 쓰지 않는다.

대상 소스가 이전 T1과 동일함을 `git diff --quiet T1 S -- src build.gradle` 종료 코드 0으로 확인했다. 원본 T1 로그/XML은 build 밖에 남아 있고 새 ZIP의 `prior-t1/`에 이번 충돌과 관련된 두 XML·기본 로그·실행 JSON·집계·대응 CSV만 분리한다. 같은 실패 수치를 늘리기 위한 재실행은 하지 않았다.

이력 자료가 확보되어 위 수정이 가능하면 다음 순서로 진행한다: SQL 본문 blob 보존 개명·필요 fixture 보완 → 코드/시험 C 커밋 → 기존 증거 보존 후 Gradle clean으로 옛 리소스 제거 → 두 실패 클래스 회귀 → C의 형식/컴파일/전체 test/build → 전용 PostgreSQL/Redis 8개 선택 묶음 → 일반+demo 신규/구버전 업그레이드 검사 → 최종 리소스/jar의 필수 Migration·옛 이름 부재 대조 → 자원 정리·새 보고. 일반 변경이므로 이전 선택 109건을 그대로 재사용해 마감할 수 없다. 해당 계획의 정확한 클래스/환경변수는 신규 명령서와 기존 `run-check.ps1` 소비 위치를 따른다.

## 남은 결정과 제출

필요 자료는 두 가지다.

1. 팀이 보존·지원하는 일반/demo 환경의 범위 및 각 환경에서 ownership `2026102702`와 후속 `2026102707`의 적용 여부. 적용됐다면 비민감 version/script/checksum/success 발췌, 미적용이라면 담당자와 확인 범위가 있는 확인 기록.
2. 그 범위에서 일반 ownership 개명안 또는 이미 제공된 다른 해결안 중 무엇을 채택하는지에 대한 담당 결정. 기존 demo 적용 이력의 보존 조건도 함께 확인.

이번에 DB·Redis·HTTP 서버·엔진을 생성/기동/정리하지 않았다. 기존 사용자 자원에는 접근하지 않았다. 정식 ownership 취소/완료 포트, 상태·ID·토큰·만료·잠금·부수효과 계약과 H40-06/R07/P5·화면/보존 DB 이행·운영 활성화는 계속 이월한다. 실제 공개 reader와 history→R10, R1/T1을 다시 미완료로 돌리지 않았다.

산출물은 이 보고서, `h40-migration-fix-pr-draft.md`, 로컬 보고 커밋 및 `review-h40-migration-fix-<R짧은SHA>.zip`이다. 수정 코드가 없으므로 별도 수정 커밋/C는 만들지 않았다. ZIP manifest에 S/C/R/B/U·기준 ZIP 해시·파일별 SHA-256·새 실행 NOT_RUN·과거 증거 구분을 기록한다. push·PR 생성/갱신·merge·배포는 수행하지 않았다.
