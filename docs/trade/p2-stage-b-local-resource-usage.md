# 로컬 API·프로그램·외부 작업 자원 사용보고서

## 1. 작업 식별

| 항목 | 기록 |
|---|---|
| 작업 | P2 Stage B 정식 연결·실연동 검증 |
| 작업 일시 | 2026-10-22 16:20~16:36 KST |
| 작업 브랜치 / 시작 SHA | `feature/trade-p2-integration` / `9e80cae604d2856dc3955cba6ab672fb8c01d541` |
| 프로젝트 루트 | `<PROJECT_ROOT>` |
| 실행 환경 | Windows 10 amd64, Temurin Java 17.0.19, Gradle 9.7.1 |
| 컨테이너 context·엔진 | `default`, Rancher Desktop 1.24.0, Docker server 29.5.3 |
| 외부 자원 사용 | 사용 |
| 적용 범위 | 로컬 격리 PostgreSQL과 Gradle 검증만 수행. 공유 DB·원격 배포 미수행 |

## 2. 자원별 사용 요약

| ID | 자원·버전 | 목적 | 사전 상태 | 수행 동작 | 노출 범위 | 결과 | 종료 상태 |
|---|---|---|---|---|---|---|---|
| RESOURCE-01 | Git / GitHub 공개 API | 최신 main, PR #32, ownership PR 확인 | 원격 참조 구버전 | fetch·읽기 조회 | 공개 저장소 메타데이터 | 성공 | 변경 없음 |
| RESOURCE-02 | Java 17 / Gradle 9.7.1 | 포맷·단위·통합·빌드 검증 | 설치됨 | 프로젝트 빌드·테스트 | 프로젝트 파일·Gradle 캐시 | 성공 | 데몬은 Gradle 기본 정책 유지 |
| RESOURCE-03 | Rancher Desktop / Docker | 격리 PostgreSQL 실행 | 최초 중지 | 기동·재기동·종료 | 로컬 Docker context | 성공, 정리 중 백엔드 재연결 1회 | 종료 |
| RESOURCE-04 | `postgres:16-alpine` | 실제 PostgreSQL 거래 회귀 | 이미지 기존 보유 | 임시 컨테이너 생성·삭제 | loopback 임의 포트 `32768` | 성공 | 컨테이너 삭제, 이미지 유지 |

## 3. 자원별 상세 기록

### RESOURCE-01

- 사용 목적: PR #32 병합 여부, 최신 원본 SHA, 공개 ownership PR 제공 여부 확인
- 수행 동작: `git fetch --prune upstream`, `git fetch --prune origin`, GitHub 공개 REST API 읽기
- 영향: 로컬 원격 추적 참조만 갱신. 원격 저장소 변경 없음
- 결과: PR #32 병합 확인, ownership 관련 공개 PR 없음

### RESOURCE-02

- 사용 목적: production 판매글 어댑터 및 기존 거래 회귀 검증
- 주요 실행: `gradlew.bat spotlessApply`, 선택 단위 테스트, PostgreSQL 통합·HTTP 테스트, `gradlew.bat spotlessCheck test build`
- 생성·변경: 프로젝트의 빌드 산출물과 일반 Gradle 캐시
- 비밀정보: 기록하지 않음
- 결과: 실행한 검사 모두 성공

### RESOURCE-03 / RESOURCE-04

- 사용 목적: 실제 PostgreSQL 쿼리·잠금·트랜잭션·HTTP 경로 검증
- 컨테이너: `pastport-p2-stageb-20261022163225`
- 이미지: 기존 로컬 `postgres:16-alpine`
- endpoint: `127.0.0.1:32768`, DB `pastport_stageb`
- 데이터: 테스트 합성 데이터만 사용, 공유 DB 접근 없음
- 실행: 임시 컨테이너 생성 → readiness 확인 → Gradle PostgreSQL 테스트 → 컨테이너 삭제
- 정리 이력: 최초 정리 시 Rancher Hyper-V 백엔드 응답이 끊겨 이름 검증 단계에서 안전 중단했다. `rdctl` 정상 재기동 후 정확한 컨테이너 이름을 확인해 삭제했다.
- 종료: 컨테이너 삭제 확인, Rancher Desktop `rdctl shutdown` 성공. 기존 이미지는 삭제하지 않았다.

## 4. 종료 시 잔여 자원

| 자원 | 현재 상태 | 이번 작업 생성 여부 | 유지 이유 | 정리 방법 |
|---|---|---|---|---|
| `postgres:16-alpine` 이미지 | 유지 | 아니오 | 작업 전부터 존재한 공용 캐시 | 필요 시 사용자가 별도 관리 |
| 임시 PostgreSQL 컨테이너 | 없음 | 예 | 해당 없음 | 삭제 완료 |
| Rancher Desktop | 종료 | 아니오 | 작업 전 중지 상태 복원 | 종료 완료 |

## 5. 최종 확인

- [x] 로컬 프로그램과 API 사용 목적을 기록했다.
- [x] 실제 명령·포트·DB·변경 범위를 기록했다.
- [x] 테스트 결과를 실제 실행 근거와 연결했다.
- [x] 이번 작업이 생성한 컨테이너를 삭제했다.
- [x] 비밀번호·토큰·쿠키·개인 데이터는 기록하지 않았다.
- [x] 공유 DB·배포 환경은 사용하거나 변경하지 않았다.
