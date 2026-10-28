# 로컬 API·프로그램·외부 작업 자원 사용보고서

## 1. 작업 식별

| 항목 | 기록 |
|---|---|
| 작업 | P2 Stage B 정식 연결·실연동 검증 |
| 작업 일시 | 2026-10-22 16:20~17:22 KST |
| 작업 브랜치 / 시작 SHA | `feature/trade-p2-integration` / `9e80cae604d2856dc3955cba6ab672fb8c01d541` |
| 프로젝트 루트 | `<PROJECT_ROOT>` |
| 실행 환경 | Windows 10 amd64, Temurin Java 17.0.19, Gradle 9.7.1 |
| 컨테이너 context·엔진 | `default`, Rancher Desktop 1.24.0, Docker server 29.5.3 |
| 외부 자원 사용 | 사용 |
| 적용 범위 | 로컬 격리 PostgreSQL·Redis와 Gradle 검증만 수행. 공유 DB·원격 배포 미수행 |

## 2. 자원별 사용 요약

| ID | 자원·버전 | 목적 | 사전 상태 | 수행 동작 | 노출 범위 | 결과 | 종료 상태 |
|---|---|---|---|---|---|---|---|
| RESOURCE-01 | Git / GitHub 공개 API | 최신 main, PR #32, ownership PR 확인 | 원격 참조 구버전 | fetch·읽기 조회 | 공개 저장소 메타데이터 | 성공 | 변경 없음 |
| RESOURCE-02 | Java 17 / Gradle 9.7.1 | 포맷·단위·통합·빌드 검증 | 설치됨 | 프로젝트 빌드·테스트 | 프로젝트 파일·Gradle 캐시 | 성공 | 데몬은 Gradle 기본 정책 유지 |
| RESOURCE-03 | Rancher Desktop / Docker | 격리 PostgreSQL 실행 | 최초 중지 | 기동·재기동·종료 | 로컬 Docker context | 성공, 정리 중 백엔드 재연결 1회 | 종료 |
| RESOURCE-04 | `postgres:16-alpine` | 실제 PostgreSQL 거래 회귀 | 이미지 기존 보유 | 임시 컨테이너 생성·삭제 | loopback 임의 포트 `32768` | 성공 | 컨테이너 삭제, 이미지 유지 |
| RESOURCE-05 | `redis:7-alpine` | 실제 Refresh 회전·로그아웃 차단 | 이미지 없음 | 이미지 pull·임시 컨테이너 생성·삭제 | loopback 임의 포트 `32769` | 성공 | 컨테이너·신규 이미지 삭제 |

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

### RESOURCE-05 및 검수 보강 실행

- 사용 목적: 실제 로그인 쿠키·CSRF·Refresh Token 회전·재사용 거부·로그아웃 Access Token 차단 검증
- 컨테이너: `pastport-p2-review-redis-20261022170847`
- 이미지: 작업 중 새로 받은 `redis:7-alpine`
- endpoint: `127.0.0.1:32769`, 기본 DB, 합성 인증 데이터만 사용
- 함께 사용한 PostgreSQL: `pastport-p2-review-pg-20261022170847`, `127.0.0.1:32768`, DB `pastport_stageb_review`
- 전용 명령: 결과 문서의 전체 환경변수·Wrapper 명령과 동일하며 `--rerun-tasks --no-build-cache` 사용
- 결과: PostgreSQL 클래스 15건 + HTTP/소켓/인증 클래스 7건, failures/errors/skipped 0
- 실행 중 상태: 최초 고정 CSRF 재사용 시험 실패를 통해 인증 후 새 토큰 발급 필요를 확인하고 브라우저 흐름에 맞게 보정했다. 최종 전체 전용 실행은 성공했다.
- 정리 이력: Rancher 백엔드 응답 중단으로 readiness와 삭제가 각각 한 번 지연됐다. 정확한 컨테이너 이름을 재확인해 두 컨테이너를 삭제했다.
- 종료: 새로 받은 Redis 이미지도 삭제하고 `rdctl shutdown`으로 작업 전 중지 상태를 복원했다.

## 4. 종료 시 잔여 자원

| 자원 | 현재 상태 | 이번 작업 생성 여부 | 유지 이유 | 정리 방법 |
|---|---|---|---|---|
| `postgres:16-alpine` 이미지 | 유지 | 아니오 | 작업 전부터 존재한 공용 캐시 | 필요 시 사용자가 별도 관리 |
| 임시 PostgreSQL 컨테이너 | 없음 | 예 | 해당 없음 | 삭제 완료 |
| 임시 Redis 컨테이너·이미지 | 없음 | 예 | 해당 없음 | 삭제 완료 |
| Rancher Desktop | 종료 | 아니오 | 작업 전 중지 상태 복원 | 종료 완료 |

## 5. 최종 확인

- [x] 로컬 프로그램과 API 사용 목적을 기록했다.
- [x] 실제 명령·포트·DB·변경 범위를 기록했다.
- [x] 테스트 결과를 실제 실행 근거와 연결했다.
- [x] 이번 작업이 생성한 컨테이너를 삭제했다.
- [x] 비밀번호·토큰·쿠키·개인 데이터는 기록하지 않았다.
- [x] 공유 DB·배포 환경은 사용하거나 변경하지 않았다.

## 6. 재검수 이후 재실행 안전 조건

- HTTP/PostgreSQL 시험은 Redis DB 전체에 `FLUSHDB`를 실행하므로, 다른 데이터가 없는 전용·폐기 가능한 Redis 인스턴스만 연결한다.
- 실행자는 endpoint를 확인한 뒤에만 `P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true`를 설정한다. 이 명시 승인이 없으면 시험은 Redis 초기화 전에 실패한다.
- loopback 주소나 별도 DB 번호만으로 전용 자원임을 가정하지 않는다. 기존 개발·공유 Redis에는 이 시험을 연결하지 않는다.
- 전용 시험 후 `P2_TRADE_TEST_ENABLED=false`로 전환하고 DB·Redis 접속 환경변수와 초기화 승인 값을 제거한 뒤 일반 전체 검사를 실행한다.
- 비밀번호·토큰·쿠키 등 임시 비밀값은 문서와 로그에 남기지 않고 실행 세션 종료 전에 제거한다.

## 7. 상위 리뷰 후 보완 재실행

| 항목 | 기록 |
|---|---|
| 작업 일시 | 2026-10-23 KST |
| 실행 기준 | `5dc6ffe04e8d42f4b5876a55da6b5f8d96bbccab` 기반 보강 작업 트리 |
| PostgreSQL | `postgres:17-alpine`, `pastport-p2-review-pg`, `127.0.0.1:55432`, 합성 데이터 전용 DB |
| Redis | `redis:8-alpine`, `pastport-p2-review-redis`, `127.0.0.1:56379`, `FLUSHDB` 승인 후 사용 |
| 전용 시험 | 22건, failures/errors/skipped 0 |
| 일반 전체 검사 | 338건, failures/errors 0, skipped 22 |
| 공유 자원 | 공유 DB·Redis·배포 환경 접근 없음 |
| 종료 상태 | 두 임시 컨테이너와 새 Redis 이미지 삭제, Rancher Desktop 작업 전 중지 상태 복원 |

- 전용 시험에서는 PostgreSQL·Redis endpoint와 컨테이너 이름을 고정해 대상 자원을 확인한 뒤 `P2_TRADE_TEST_REDIS_FLUSH_ALLOWED=true`를 설정했다.
- 전용 시험 종료 후 두 컨테이너를 삭제하고 외부 시험 환경변수를 제거한 별도 Gradle 프로세스에서 일반 전체 검사를 수행했다.
- PostgreSQL 17 이미지는 기존 프로젝트 컨테이너가 사용하던 로컬 캐시이므로 유지했고, 이번 작업에서 새로 받은 Redis 8 이미지만 삭제했다.

## 8. `a9fc04b` 리뷰 보정 후 재실행

| 항목 | 기록 |
|---|---|
| 작업 일시 | 2026-10-23 KST |
| 실행 기준 | `a9fc04b` 작업 트리에서 미승인 ownership·일반 Migration을 분리한 상태 |
| PostgreSQL | 기존 `postgres:17-alpine`, `pastport-p2-correction-pg`, `127.0.0.1:32768`, 합성 데이터 전용 DB |
| Redis | 새로 받은 `redis:8-alpine`, `pastport-p2-correction-redis`, `127.0.0.1:32769`, 명시 승인 후 초기화 |
| 전용 시험 | 22건, failures/errors/skipped 0 |
| 일반 전체 검사 | 354건, failures/errors 0, skipped 22 |
| 공유 자원 | 공유 DB·Redis·배포 환경 접근 없음 |
| 종료 상태 | 두 임시 컨테이너와 새 Redis 이미지 삭제, Rancher Desktop 중지 상태 복원 |

- 외부 전용 시험은 pending 거래 DDL과 테스트 전용 ownership fixture를 사용했다. 정식 ownership E2E로 집계하지 않는다.
- 첫 정리 시 Rancher Hyper-V 백엔드 응답이 끊겨 삭제가 실패했다. Rancher를 재기동하고 정확한 컨테이너 이름과 이미지를 재확인한 뒤 삭제했다.

## P4 거래완료 작업 추가 기록

| 항목 | 결과 |
|---|---|
| 사용 자원 | Git·파일 편집, Temurin Java 17.0.19, 프로젝트 Gradle Wrapper |
| 외부 자원 | PostgreSQL·Redis·Rancher·Docker·로컬 API·공유 서비스 미사용 |
| 실행 범위 | `gradlew.bat spotlessApply spotlessCheck classes testClasses test`에 15개 관련 클래스 선택, 100건 성공 |
| 잔여 프로세스 | Gradle `--no-daemon` 단일 실행 종료, 신규 외부 프로세스 없음 |

P4 전용 `TradeP4PostgresIntegrationTest`는 `P4_TRADE_TEST_ENABLED=true`일 때만 동작하도록 작성하고 `testClasses`로 컴파일했다. P3 pending 제약, 실제 거래 측 change review·판매 중지 어댑터를 test fixture에 연결했지만 DB02의 거래/SOLD 저장 실패 주입은 `CODE_TODO`다. 환경변수를 설정하거나 PostgreSQL을 기동·접속하지 않았으므로 모든 P4 DB 시험의 실제 결과는 `NOT_RUN`이다.
- 최종 확인에서 `pastport-p2-correction-*` 컨테이너와 `redis:8-alpine` 이미지가 각각 0건임을 확인했다.
- 작업 전부터 있던 PostgreSQL 17 이미지는 유지하고 `rdctl shutdown`으로 작업 전 중지 상태를 복원했다.

## 9. 외부 자료 비의존 선행작업 (2026-10-26)

| 항목 | 기록 |
|---|---|
| 실행 기준 | `6193bbeac53a6c698b77e34527e5bd348c5e47a9`, 시작 작업 트리 clean |
| 사용 자원 | Git·파일 편집, Temurin Java 17.0.19, 프로젝트 Gradle Wrapper |
| 주요 명령 | `spotlessApply`, `spotlessCheck classes testClasses`, 외부 자원 없는 거래 단위시험 2개 선택 실행 |
| 외부 자원 | DB·Redis·Rancher·로컬 API·공유 서비스 미사용 |
| DB 사전점검 | SQL·안내 문서만 정적 검토, 실제 실행 `NOT_RUN` |
| 생성·변경 | 저장소 문서·포트 Javadoc, `build` 결과와 일반 Gradle 캐시 |
| 종료 상태 | 컨테이너·이미지·네트워크·외부 데이터 생성 없음. Gradle 데몬과 캐시는 기본 정책대로 유지 |

셸의 기본 Java 8은 Gradle 요구 버전을 충족하지 않아 첫 검사가 실행 전에 실패했다. 시스템 전역 설정은 변경하지 않고
각 검증 명령의 프로세스 환경에만 `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`을 지정했다.

## 10. P3 예약 취소·변경 확인 구현

| 항목 | 기록 |
|---|---|
| 작업 기준 | P2 `72e3d6d`에서 P3 브랜치 생성, 원본 main `7ae4ccd` R07 통합 |
| 사용 자원 | Git·파일 편집, Temurin Java 17.0.19, 프로젝트 Gradle Wrapper |
| 외부 자원 | DB·Redis·Rancher·로컬 API·공유 서비스 미사용 |
| DB 자료 | pending DDL과 opt-in PostgreSQL 시험 코드만 작성, 실제 실행 `NOT_RUN` |
| 생성·변경 | 소스·시험·문서, `build` 결과와 일반 Gradle 캐시 |
| 종료 상태 | 컨테이너·이미지·네트워크·외부 데이터 생성 없음. Gradle 데몬과 캐시는 기본 정책대로 유지 |

기본 Java 8이나 시스템 전역 설정을 변경하지 않고 각 Gradle 프로세스에만 Java 17 `JAVA_HOME`을 지정했다. 최종 검사
명령과 결과는 `p3-result.md`에 기록한다.
