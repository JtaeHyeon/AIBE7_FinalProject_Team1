# 백엔드 초기 세팅 가이드

이 문서는 AIBE7 Final Project Team 1 백엔드의 초기 개발 환경, 공통 설정, 패키지 구조와 실행 방법을 설명합니다.

## 1. 기술 기준

- Java 17
- Spring Boot 4.1.1
- Gradle Wrapper 9.7.1
- PostgreSQL 17
- Redis 8
- Spring Security + OAuth2 Resource Server JWT
- Spring Data JPA / Redis
- Flyway
- springdoc OpenAPI
- JUnit 5 / Mockito
- Spotless

초기 범위에서는 다음 기술을 사용하지 않습니다.

- pgvector
- RAG
- OAuth 소셜 로그인
- Kafka
- Kubernetes
- 배포 자동화

## 2. 주요 의존성

`build.gradle`에 다음 항목이 설정되어 있습니다.

- Spring Web MVC
- Spring Data JPA
- Spring Data Redis
- Spring Security
- Bean Validation
- OAuth2 Resource Server
- PostgreSQL Driver
- Flyway Core / PostgreSQL 지원 모듈
- springdoc OpenAPI UI
- JUnit 5 / Mockito
- Spotless

JWT 처리는 OAuth2 Resource Server 기반입니다. 현재 단계에는 로그인 및 토큰 발급 기능이 없으며, 외부에서 발급된 HS256 JWT를 검증하는 기본 구성만 포함합니다.

## 3. 사전 준비

로컬 개발 환경에 다음 도구가 필요합니다.

- JDK 17
- IntelliJ IDEA
- Docker Desktop 또는 Docker Engine + Docker Compose

IntelliJ에서 프로젝트 루트 디렉터리를 Gradle 프로젝트로 엽니다.

```text
AIBE7_FinalProject_Team1
```

IntelliJ 설정에서 Project SDK와 Gradle JVM을 모두 Java 17로 지정합니다.

## 4. 환경 변수 설정

예시 파일을 복사해 로컬 전용 `.env` 파일을 만듭니다.

```bash
cp .env.example .env
```

`.env`는 Git에서 제외됩니다. 실제 운영 비밀번호, JWT 키, API 키를 저장소에 커밋하지 않습니다.

주요 환경 변수는 다음과 같습니다.

| 변수 | 용도 | 필수 여부 |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | Docker PostgreSQL 비밀번호 | 로컬 Compose 필수 |
| `DB_URL` | 애플리케이션 JDBC URL | 로컬 기본값 제공 |
| `DB_USERNAME` | DB 사용자 | 로컬 기본값 제공 |
| `DB_PASSWORD` | 애플리케이션 DB 비밀번호 | 필수 |
| `SUPABASE_DB_URL` | Supabase Session pooler JDBC URL | `supabase` 프로필 필수 |
| `SUPABASE_DB_USERNAME` | Supabase DB 사용자 (`postgres.<project-ref>`) | `supabase` 프로필 필수 |
| `SUPABASE_DB_PASSWORD` | Supabase Database Password | `supabase` 프로필 필수 |
| `SUPABASE_DB_MAX_POOL_SIZE` | 애플리케이션 DB 최대 연결 수 | 선택, 기본값 `5` |
| `REDIS_HOST` | Redis 호스트 | 로컬 기본값 제공 |
| `REDIS_PORT` | Redis 포트 | 로컬 기본값 제공 |
| `REDIS_PASSWORD` | Redis 비밀번호 | 선택 |
| `JWT_SECRET` | HS256 JWT 검증 키 | 필수, 32바이트 이상 |
| `AI_API_KEY` | 향후 AI 연동 API 키 | 현재 미사용 |
| `CORS_ALLOWED_ORIGINS` | 허용할 프런트엔드 Origin | 로컬 기본값 제공 |
| `LOCAL_STORAGE_ROOT` | 로컬 파일 저장 경로 | 로컬 기본값 `./storage` |

주의 사항:

- Docker Compose는 프로젝트 루트의 `.env`를 자동으로 읽습니다.
- Spring Boot는 프로젝트 루트의 `.env`를 읽습니다. IntelliJ의 Working directory가 프로젝트 루트인지 확인합니다.
- `DB_PASSWORD`와 `POSTGRES_PASSWORD`는 로컬 환경에서 같은 값으로 설정합니다.
- `JWT_SECRET`은 예시 값을 그대로 사용하지 말고 환경마다 안전한 값으로 교체합니다.

## 5. PostgreSQL과 Redis 실행

초기 `compose.yaml`에는 PostgreSQL과 Redis만 포함합니다.

PostgreSQL 볼륨이 처음 생성될 때 애플리케이션 DB `aibe7`과 분리된 테스트 DB `aibe7_test`를 함께 생성합니다. 이미 초기화된 PostgreSQL 볼륨에는 초기화 스크립트가 다시 실행되지 않으므로 테스트 DB를 직접 생성하거나 볼륨을 초기화해야 합니다.

```bash
docker compose up -d
docker compose ps
```

서비스를 중지할 때는 다음 명령을 사용합니다.

```bash
docker compose down
```

`docker compose down -v`는 로컬 DB와 Redis 볼륨 데이터까지 삭제하므로 초기화가 필요한 경우에만 사용합니다.

Object Storage 구현체는 아직 결정하지 않았습니다. 현재는 `FileStorage` 인터페이스와 `local`, `test` 프로필에서 동작하는 `LocalFileStorage`만 제공합니다.

### Supabase PostgreSQL 사용

Supabase Dashboard의 **Connect** 화면에서 **Session pooler** 연결 정보를 확인합니다. Spring Data JPA처럼 지속적으로 실행되는 ORM 애플리케이션은 Transaction pooler(6543)가 아닌 Session pooler(5432)를 사용합니다. IPv6 연결이 가능한 실행 환경에서는 Direct connection도 사용할 수 있습니다.

`.env`에 다음 값을 설정합니다. 비밀번호에 `&`, `#`, `?`, 공백 같은 예약 문자가 있으면 URL에 직접 포함하지 말고 `SUPABASE_DB_PASSWORD`에 원문 그대로 입력합니다.

```dotenv
SPRING_PROFILES_ACTIVE=supabase
SUPABASE_DB_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<database-password>
```

이후 애플리케이션을 실행하면 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 적용하고 Hibernate가 스키마를 검증합니다.

```bash
./gradlew bootRun
```

Supabase 연결 설정은 SSL을 강제하고 HikariCP 최대 연결 수를 기본 5개로 제한합니다. 운영 규모와 Supabase 프로젝트의 연결 한도를 확인한 후 풀 크기를 조정합니다. 실제 비밀번호나 연결 문자열은 Git에 커밋하지 않습니다.

이 프로필은 PostgreSQL만 Supabase로 전환합니다. Redis가 필요한 기능은 별도의 관리형 Redis 또는 로컬 Docker Redis 설정을 계속 사용해야 합니다.

## 6. 환경별 Spring 설정

설정 파일은 다음과 같이 구분합니다.

| 파일 | 역할 |
| --- | --- |
| `application.yaml` | 공통 설정 및 기본 `local` 프로필 |
| `application-local.yaml` | 로컬 PostgreSQL·Redis 설정 |
| `application-test.yaml` | 테스트 DB·Redis·JWT·저장소 설정 |
| `application-supabase.yaml` | Supabase PostgreSQL 연결 및 풀 설정 |
| `application-prod.yaml` | 운영 DB·Redis 및 운영 안전 설정 |

공통 기준:

- 기본 활성 프로필은 `local`입니다.
- Flyway는 모든 환경에서 활성화합니다.
- JPA Open Session in View는 비활성화합니다.
- Hibernate `ddl-auto`는 `validate`를 사용합니다.
- SQL 출력은 비활성화합니다.
- DB 비밀번호, JWT 키, AI API 키는 환경 변수로 관리합니다.
- CORS 허용 Origin은 `CORS_ALLOWED_ORIGINS`로 관리합니다.
- 로그에는 비밀번호, 토큰, 개인정보를 출력하지 않습니다.

루트의 `.env`는 `spring.config.import`를 통해 로컬 실행 시 자동으로 읽습니다. 운영 환경에서는 `.env` 대신 배포 환경의 환경 변수 또는 Secret 관리 기능을 사용합니다.

## 7. IntelliJ 실행

1. `SpringTestCiApplication`을 실행 대상으로 선택합니다.
2. Run Configuration의 JRE를 Java 17로 지정합니다.
3. 프로젝트 루트에 `.env`가 있는지 확인합니다. 필요하면 Run Configuration에 환경 변수를 직접 등록해 `.env` 값을 재정의합니다.
4. PostgreSQL과 Redis가 실행 중인지 확인합니다.
5. 애플리케이션을 실행합니다.

별도로 프로필을 지정하지 않으면 `local` 프로필이 사용됩니다. 명시적으로 지정하려면 Active profiles에 `local`을 입력합니다.

실행 후 OpenAPI UI는 다음 주소에서 확인합니다.

```text
http://localhost:8080/swagger-ui.html
```

## 8. 공통 코드

공통 코드는 `src/main/java/org/example/springtestci/common` 아래에 구성합니다.

```text
common
├── audit
│   ├── AuditConfig
│   └── BaseTimeEntity
├── config
│   ├── CorsConfig
│   ├── CorsProperties
│   ├── OpenApiConfig
│   └── RequestIdFilter
├── error
│   ├── CommonException
│   ├── ErrorCode
│   ├── ErrorResponse
│   └── GlobalExceptionHandler
├── response
│   └── ApiResponse
├── security
│   ├── JwtProperties
│   └── SecurityConfig
├── storage
│   ├── FileStorage
│   └── LocalFileStorage
└── time
    └── TimeConfig
```

적용된 공통 기능:

- 공통 API 성공 응답
- 공통 오류 코드 및 오류 응답
- Bean Validation 오류 처리
- 예상하지 못한 예외의 안전한 응답 처리
- `X-Request-Id` 생성·응답 헤더 전달·MDC 기록
- 생성·수정 시각 자동 기록
- UTC 기준 `Clock`
- OpenAPI 기본 정보와 JWT Bearer 인증 스키마
- 환경 변수 기반 CORS 설정
- Stateless Security와 JWT 검증
- 로컬 파일 저장 구현

## 9. 도메인 패키지 구조

초기 도메인은 다음과 같습니다.

```text
auth
product
ownership
document
history
listing
trade
ai
```

각 도메인은 동일한 계층 구조를 사용합니다.

```text
<domain>
├── controller
├── service
├── repository
├── entity
└── dto
```

구현 원칙:

- Controller에는 비즈니스 로직을 작성하지 않습니다.
- Controller에서 Repository를 직접 호출하지 않습니다.
- API 입력과 출력에는 DTO를 사용합니다.
- Entity를 API 응답으로 직접 반환하지 않습니다.
- 연관관계는 기본적으로 Lazy Loading을 사용합니다.
- 주요 비즈니스 로직에는 테스트를 작성합니다.

## 10. Flyway 규칙

초기 마이그레이션은 다음 파일입니다.

```text
src/main/resources/db/migration/V1__baseline.sql
```

스키마를 변경할 때 기존 마이그레이션을 수정하지 않고 새 파일을 추가합니다.

```text
V2__create_users.sql
V3__create_products.sql
```

파일명은 `V버전__설명.sql` 형식을 사용합니다.

## 11. 보안 기본값

- 세션을 생성하지 않는 Stateless 방식입니다.
- CSRF는 REST API 기준으로 비활성화합니다.
- JWT는 HS256 방식으로 검증합니다.
- Swagger/OpenAPI와 기존 정적 메인 페이지는 인증 없이 접근할 수 있습니다.
- 그 외 요청은 기본적으로 인증이 필요합니다.
- 운영 로그에 JWT, 비밀번호, API 키, 개인정보를 기록하지 않습니다.

OAuth 소셜 로그인과 자체 로그인·토큰 발급 기능은 초기 범위에 포함하지 않습니다.

## 12. 코드 포맷 및 검증

Java 코드는 Spotless의 Google Java Format을 적용합니다.

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew test
./gradlew build
```

커밋 전 최소 `spotlessCheck`, `test`, `build`가 모두 성공하는지 확인합니다.
