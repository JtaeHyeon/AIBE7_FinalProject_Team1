# 과거 PR 초안 — 현재 제출에 사용하지 않음

> v1.3 현재 PR #55는 일반 ownership `2702`/카드 요약 `2802` 기준 복구를 적용한다. 아래 v1.2 안내와 초안의 `2702.1` 채택·일반 DB 업그레이드 미지원은 역사 기록이다. 현재 결과는 [일반 기준 복구 결과](h40-general-authority-result.md)를 따른다.

> 2026-10-29 v1.2: 아래는 `28ad18f` 조사 당시 초안이다. 당시의 코드 변경 없음 / NOT_RUN / 원격 `44bd3f2` / BLOCKED 문구는 현재 후보나 이번 PR의 상태가 아니다. 현행 ownership 및 보정된 Migration을 개발 기준으로 채택했고, 옛 일반 DB 자동 업그레이드는 지원 범위에서 제외해 요청 시 별도 처리한다. 현재 결과·지원 범위는 [로컬 검증 결과](h40-migration-local-validation.md)와 [팀원 인계](h40-migration-team-handoff.md)를 사용한다. 현재 PR은 지정 개인 포크 `feat/h40-history-integration-20261028`에서 팀 `main`으로 제출하는 일반 PR을 목표로 한다. 아래 원문은 역사 기록으로 보존한다.

제안 제목: **fix(trade): H40 history 연결 및 Migration 공존 보정 (적용 이력 확인 대기)**

- 제안 base: `prgrms-aibe-devcourse/AIBE7_FinalProject_Team1:main`.
- 제안 head: 개인 포크의 `test/h40-final-gate0-20261028` 또는 후속 원격 작업 승인 시 정할 독립 제출 브랜치.
- 원격 head는 아직 과거 보고 `44bd3f2`이며 로컬 통합/결과와 다르다. 이 초안으로 PR을 생성하거나 branch를 이동하지 않는다.

## 문제와 현재 변경

수용 H40 `003058e`와 고정 main B `f5ad102`를 통합한 T0 `22489ca`, 포트 호출부 호환성과 시험 경계를 보완한 T1 `50c9632`를 보존한다. 실제 history 신규 등록의 facts 버전 증가·P3 공개 안전 사본·양측 확인 무효화·기존 R10 접수를 연결하며, 구매자 사본의 자유 문자열 제한과 완료/history 경합 오류 구분을 유지한다.

일반과 demo locations에 서로 다른 `V2026102702`가 있어 기존 demo 업그레이드 시험 3건이 실패한다. 일반 ownership Migration의 보존 환경 적용 여부가 미확인이므로 이번 조사에서 SQL/파일명/시험을 바꾸지 않았다. 기존 demo 이력·checksum 보존 근거는 있지만 일반 ownership 미적용을 증명하지는 않는다.

검토안은 **미적용 확인을 받은 경우에만** 일반 ownership을 비어 있는 `2026102702_1` 구간으로 개명하고 SQL blob·demo 구버전 이력을 보존하는 것이다. 이는 아직 반영된 수정이 아니며 담당 결정·현재 버전 목록 재확인 후 채택해야 한다. 현재 C는 미생성이고 소스는 T1과 같다.

## 검증 및 보류 사유

- 과거 T1 기본: 757 tests / 699 PASS / 3 failures / 0 errors / 55 SKIPPED. 전체 build 실패.
- 과거 T1 전용 PostgreSQL/Redis 선택: 109/109 PASS. 거래 ownership 포트 등 일부는 `DB_PARTIAL`이다.
- 이번 실행: 코드 변경 없음, 시험/build **NOT_RUN**. 소스·locations·의존·Git/PR 기록 조사와 이전 원본 증거 대조만 수행했다.
- 현재 판정: **BLOCKED / APPLICATION_HISTORY**. 일반/demo 충돌과 F-02/F-03 FAIL은 남아 있다. 수정·새 회귀 후 실제 C와 결과로 이 본문을 갱신해야 한다.

## 이월

거래 기능 기본 OFF·pending 격리를 유지한다. 공개 reader는 공급·검사된 실제 모듈이다. 실제 AI/S3/메일 호출이나 공유 DB 이행은 수행하지 않았다. 정식 ownership 취소·완료 포트 및 상태/ID/토큰/만료/잠금/필수 부수효과 인수, H40-06, R07, P5, 화면·운영 활성화·배포는 별도다. H40 리뷰 준비를 정식 이전 전체 인수나 병합 승인으로 표현하지 않는다.
