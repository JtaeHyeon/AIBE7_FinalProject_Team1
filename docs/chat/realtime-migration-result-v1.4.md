# feat(chat): 채팅 실시간 전달과 로컬 검증 추가

## 작업 내용

- 기준 저장소·브랜치·커밋: `goarns77/AIBE7_FinalProject_Team1`, `feat/chat-api-namespace`, `1a1aaf2`
- 참조 원본 커밋: `prgrms-aibe-devcourse/AIBE7-Project2-Team02`, `a65b9918`
- 기존 HTTP API, `DefaultChatService`, 채팅 JPA 어댑터, `V2__create_chat_tables.sql`을 재사용했다.
- `/ws-stomp` 네이티브 WebSocket/STOMP 연결과 `/sub/chat/room/{roomId}` 구독을 추가했다.
- STOMP `CONNECT`에서 Bearer JWT를 검증하고, 정확한 방 구독 목적지와 고정 참여자 권한을 검사한다.
- 클라이언트의 모든 STOMP `SEND`를 거부하고 메시지 저장은 기존 HTTP API 한 경로로 유지한다.
- 저장 트랜잭션에 `afterCommit` 동기화를 등록해 커밋된 `ChatMessageResponse`만 전파한다.
- 실제 서버·두 STOMP 세션·HTTP 클라이언트를 사용하는 테스트 클라이언트를 `src/test`에 추가했다.
- 형식 불량·서명 불일치·만료·누락 JWT를 실제 STOMP CONNECT에서 각각 거부하는 시험을 추가했다.
- 실제 서비스 트랜잭션을 rollback-only로 종료해 DB 행 부재와 소켓 미수신을 함께 검증했다.
- 고정 `Thread.sleep(200)` 대신 서버의 `SessionSubscribeEvent` 처리 완료를 기다린다.
- 데이터베이스 스키마 변경은 없다. 기존 Migration 파일도 수정하지 않았다.

## 통신 계약

| 구분 | 실제 계약 |
|---|---|
| HTTP 전송 | `POST /api/v1/chat-rooms/{roomId}/messages`, `SendChatMessageRequest`, `ApiResponse<ChatMessageResponse>`, Bearer JWT, 성공 200 |
| 연결 | 네이티브 WebSocket `/ws-stomp`, STOMP `CONNECT`의 `Authorization: Bearer <JWT>`에서 인증 |
| Origin | `app.cors.allowed-origins`에 명시된 Origin만 허용 |
| 구독 | `/sub/chat/room/{roomId}`만 허용, 구독 시 `ChatRoomRepository`로 고정 참여자 확인 |
| 발행 | 클라이언트 STOMP `SEND` 전부 거부, 서버만 커밋 후 구독 목적지로 전파 |
| 수신 DTO | `messageId: long`, `roomId: long`, `senderId: long`, `content: String`, `createdAt: Instant`, 모두 non-null |
| 오류 | HTTP는 기존 공통 오류 응답, STOMP 인증·인가 오류는 연결/채널 단계에서 ERROR 및 연결 거부 |

HTTP handshake transport는 `/ws-stomp`에서 허용하지만, 유효한 STOMP `CONNECT` 인증이 완료되기 전에는 구독할 수 없다. 브라우저가 handshake 요청에 임의 Authorization 헤더를 넣을 수 없는 제약을 고려한 선택이다.

## 변경 이유

- 기존 구현은 저장·조회만 제공해 상대 사용자가 새로고침 없이 메시지를 받을 수 없었다.
- HTTP 전송을 유지해 메시지 저장 경로와 업무 규칙을 복제하지 않았다.
- 애플리케이션 이벤트 대신 현재 트랜잭션의 `afterCommit` 콜백을 명시적으로 등록해 롤백 메시지의 선전파를 차단했다.
- 원본의 연결·구독 구조만 참고하고 파일·견적·거래 UI 및 원본 인증 의존성은 이식하지 않았다.
- Spring Boot 4.1의 Jackson 3 메시지 변환을 위해 `JacksonJsonMessageConverter`를 명시적으로 등록했다.
- 신규 의존성은 현재 Spring 버전에 맞는 `spring-boot-starter-websocket` 하나다.
- 공통 `SecurityConfig`에는 STOMP CONNECT 인증을 위한 handshake 경로 허용만 추가했다. 기존 REST 인증·CSRF·CORS 정책은 변경하지 않았다.

## 테스트 결과

### 실행 결과

| 검증 | 환경 | 결과 |
|---|---|---|
| 실시간 통합 테스트 | 실제 임베디드 HTTP 서버 + 네이티브 WebSocket/STOMP + H2 PostgreSQL 모드 + Flyway V1~V2 | 통과 |
| 두 사용자 송수신 | S·Q가 같은 방을 구독하고 Q→S, S→Q HTTP 전송 및 양쪽 자동 수신 | 통과 |
| 저장·수신·이력 대조 | HTTP 응답, 두 수신 DTO, JPA 재조회 식별자·발신자·본문·시각 | 통과 |
| JWT·Origin | 유효 JWT 연결, 형식 불량·서명 불일치·만료·누락 JWT 거부, 미허용 Origin handshake 거부 | 통과 |
| 구독·발행 제한 | 참여자 허용, 비참여자·와일드카드 구독 및 STOMP SEND 거부 | 단위 테스트 통과 |
| 커밋·롤백 전파 | 커밋 전 미전파와 `afterCommit` 전파 단위 시험, 실제 PostgreSQL rollback의 DB 행 부재·소켓 미수신 | 통과 |
| PostgreSQL | Compose `postgres:17-alpine`의 PostgreSQL 17.11, 전용 `aibe7_test`, 실행별 임시 스키마 | 통과 |

`ChatRealtimeIntegrationTest`는 화면 대신 사용하는 동등한 실제 통신 클라이언트다. 완성 UI와 브라우저 수동 시험은 이번 실행에 포함하지 않았다.

### RT 체크리스트

| ID | 상태 | 근거·잔여 항목 |
|---|---|---|
| RT-01 | 통과 | 전체 채팅 자동 테스트 실행 결과 참조 |
| RT-02 | 통과 | 격리 H2와 전용 PostgreSQL 17.11에서 Flyway·JPA validate·서버 기동 확인 |
| RT-03 | 통과 | 기존 `DefaultChatServiceTest`의 방 재사용 검증 |
| RT-04 | 통과 | Q HTTP 전송 후 S 실제 STOMP 자동 수신 |
| RT-05 | 통과 | S HTTP 답장 후 Q 실제 STOMP 자동 수신 |
| RT-06 | 통과 | HTTP·양쪽 수신·JPA 재조회 값 대조 |
| RT-07 | 부분 확인 | HTTP 응답·실시간 수신의 동일 ID 병합 검증 통과, 브라우저 화면 렌더링은 잔여 |
| RT-08 | 통과 | PostgreSQL에서 HTTP 저장·JPA 재조회 값과 실시간 수신 DTO 대조 |
| RT-09 | 통과 | 기존 읽기 위치·미확인 Repository/Service 테스트 재사용 |
| RT-10 | 통과 | 공백 입력 저장 거부 및 전달 경계 미호출 |
| RT-11 | 통과 | 실제 CONNECT에서 유효·형식 불량·서명 불일치·만료·누락 토큰 검증 |
| RT-12 | 통과 | 참여자 허용·비참여자 거부 인터셉터 테스트 |
| RT-13 | 통과 | 와일드카드·미등록 형식 구독 및 모든 클라이언트 SEND 거부 |
| RT-14 | 통과 | R과 R2를 각각 구독한 실제 세션에서 R 메시지의 R2 미수신 확인 |
| RT-15 | 통과 | 기존 비활성 판매글 방 생성 제한·기존 참여자 대화 유지 테스트 |
| RT-16 | 통과 | 연결 해제 중 누락 메시지를 재접속·재구독 뒤 HTTP 이력으로 복원하고 ID 중복 제거 |
| RT-17 | 통과 | 단위 경계 시험과 PostgreSQL 실제 rollback의 DB 행 부재·소켓 미수신 검증 |
| RT-18 | 통과 | fixture는 중첩 `TestConfiguration`, 미허용 Origin 실제 handshake 거부 |

### CV 체크리스트

| ID | 상태 | 근거·잔여 항목 |
|---|---|---|
| CV-01 | 통과 | Controller→Service→Repository 책임 유지, 실시간 코드는 `chat.realtime`에 한정 |
| CV-02 | 통과 | 기존 HTTP 계약 유지, STOMP 계약을 이 문서에 기록 |
| CV-03 | 통과 | 커밋·롤백 단위 검증과 PostgreSQL 실제 저장·rollback 통합 검증 |
| CV-04 | 통과 | 기존 Migration을 수정하지 않고 PostgreSQL 임시 스키마에 V1~V2 적용·정리 확인 |
| CV-05 | 통과 | 토큰·본문 로그 없음, DTO `toString()` 본문 마스킹 유지, 테스트 fixture 격리 |
| CV-06 | 통과 | `spotlessCheck`, 전체 `test`, `build` 통과. 원격 CI·PR은 미실행 |

### 최종 Gradle 검증

```powershell
.\gradlew.bat spotlessCheck test build --rerun-tasks --no-build-cache
```

- 결과: `BUILD SUCCESSFUL in 1m 30s`
- 실행 작업: `spotlessCheck`, 전체 `test`, `build`
- 기본 전체 검증은 H2 경로를 사용했고, PostgreSQL 전용 통합 검증은 아래 명령으로 별도 강제 재실행했다.

### PostgreSQL 실행 결과와 재실행

- 실행 환경: Rancher Desktop Compose, `postgres:17-alpine`, PostgreSQL 17.11
- 대상 DB: 로컬 전용 `aibe7_test`
- 격리: 실행마다 `chat_realtime_<식별자>` 스키마 생성, 종료 후 `DROP SCHEMA ... CASCADE`
- 결과: 통합 테스트 10건, 실패 0건. 종료 후 `chat_realtime_%` 스키마 부재 확인

Docker Desktop 또는 전용 PostgreSQL을 기동한 뒤 다음 환경 변수를 지정한다.

```powershell
$env:CHAT_REALTIME_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/aibe7_test'
$env:CHAT_REALTIME_TEST_DB_USER = 'aibe7'
$env:CHAT_REALTIME_TEST_DB_PASSWORD = '<local-test-password>'
.\gradlew.bat test --tests "org.example.springtestci.chat.realtime.ChatRealtimeIntegrationTest" --rerun-tasks --no-build-cache
```

테스트는 URL에 `test`가 없으면 실행을 거부하고, JDBC metadata로 실제 DB 제품이 PostgreSQL인지 검증한다. 매 실행마다 별도 스키마를 생성한 뒤 종료 시 삭제한다.

## 확인 사항

- 보강 착수 기준은 `feat/chat-api-namespace@59b3c1f`이며, `upstream/main@0c3f67b`을 작업 브랜치에 병합했다.
- main의 인증 공개 경로와 `JwtEncoder` 변경을 그대로 보존하고 `/ws-stomp` 허용 경로만 함께 통합했다.
- 실제 회원·판매글 Reader, 운영 JWT 계약, 공유 DB, 신규 ERD, 거래 상태 API 연결은 계속 외부 대기다.
- 현재 모델은 전역 `messageId`와 별도 읽기 위치 테이블을 사용한다. 이를 신규 ERD 전환 완료로 보고하지 않는다.
- HTTP 성공 코드·응답 래퍼·CSRF 차이는 기존 계약을 유지했다.
- 브라우저 화면, HTTP 응답과 소켓 이벤트의 UI 중복 제거, 재연결·이력 복원은 후속 클라이언트 작업이다.

### 외부 연결 대기

| 대상 | 현재 연결점 | 필요한 결과물 | 재개 조건 |
|---|---|---|---|
| 회원 | `ChatUserReader` | 사용자 식별·닉네임 조회 구현 | 승인된 회원 모듈 제공 |
| 판매글·제품 | `ChatListingReader` | 판매자·제품·소유자·상태·제목 조회 구현 | 승인된 조회 계약 제공 |
| 인증 | HTTP Resource Server와 STOMP `JwtDecoder` | 최종 토큰 claim·세션·CSRF 계약 | 공통 보안 기준 확정 |
| 공유 DB·신규 ERD | 기존 채팅 JPA 어댑터 | 승인 스키마·식별자 전환 기준 | 별도 DB/ERD 작업 승인 |
| 거래 | 연결 없음 | 구매 요청·예약·취소·완료 계약 | 거래 담당 구현 제공 |

## PR 본문 초안

### 작업 내용

- 기존 HTTP 메시지 저장 뒤 커밋된 메시지를 STOMP 구독자에게 전달한다.
- STOMP JWT 연결 인증, 채팅방 참여자 구독 인가, 클라이언트 SEND 차단을 추가한다.
- 실제 두 사용자 소켓 통합 테스트와 전달 경계 단위 테스트를 추가한다.

### 변경 이유

- 기존 채팅 저장·조회 API를 유지하면서 새로고침 없는 수신을 제공하기 위해서다.
- 단일 저장 경로와 롤백 비전파를 보장하기 위해 HTTP 저장과 `afterCommit` 전달을 분리했다.

### 테스트 결과

- H2 PostgreSQL 모드의 Flyway·JPA·실제 HTTP/WebSocket 통합 테스트 통과.
- PostgreSQL 17.11 전용 DB에서 Flyway·JPA·HTTP/WebSocket·실제 rollback 통합 테스트 통과.
- `spotlessCheck`, 전체 `test`, `build` 통과.

### 확인 사항

- 실제 외부 Reader·운영 인증·공유 DB·신규 ERD·거래 연동은 별도 대기다.
- 공통 보안 변경은 `/ws-stomp` handshake 허용 두 경로뿐이다.
- 사람의 리뷰·원격 CI·승인·병합은 수행되지 않았다.
