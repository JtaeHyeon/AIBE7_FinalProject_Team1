# 채팅 기본 구현 결과

상태: 부분 완료 / 실행일: 2026-10-14 / 대상 저장소: Pastport / 브랜치: `feat/chat-api-namespace`

## 1. 이번 실행

완료한 단계: 기존 골격 분석, 채팅 Service·JPA 저장 어댑터·V2 Migration 구현, 더블체크 보강, 단위·H2 통합 검증

다음에 시작할 단계: 실제 판매글·회원 모듈의 조회 어댑터 연결 후 기능 플래그 활성화와 JWT·PostgreSQL HTTP 통합 검증

실행 환경과 외부 모듈의 실제/Fake 여부: Java 17, H2 PostgreSQL 모드. Repository 통합 테스트는 Hibernate create-drop 스키마의 실제 JPA/H2를 사용했고, Migration 테스트는 별도 H2에 Flyway V1~V2를 적용했다. 두 검증을 Flyway+JPA validate 통합 검증으로 합산하지 않는다. Service의 판매글·회원 경계는 Mockito를 사용했으며 일반 실행용 Fake는 추가하지 않았다.

## 2. 변경 파일

| 경로 | 구분 | 역할 | 참고 원본 파일 |
|---|---|---|---|
| `chat/service/DefaultChatService.java` | 신규 | 5개 기능, 권한·상태·저장 흐름 | `ChatService.java`, `ChatMessageService.java` |
| `chat/service/ChatListingReader.java` | 수정 | 판매글 벌크 조회 경계 | 원본 의존성 제거 후 Pastport 경계 유지 |
| `chat/service/ChatUserReader.java` | 신규 | 닉네임 벌크 조회 경계 | 원본 회원 조회 패턴만 참고 |
| `chat/repository/ChatMessageRepository.java` | 수정 | 최신 메시지·새 메시지 조회 계약 | `ChatMessageRepository.java` |
| `chat/repository/jpa/*` | 신규 | 방·메시지·확인 위치 실제 JPA 어댑터 | 원본 저장·최근 메시지 쿼리 선별 이식 |
| `db/migration/V2026101302__create_chat_tables.sql` | 신규 | 채팅 테이블·내부 FK·UNIQUE·인덱스 | Pastport 전용 신규 스키마 |
| `chat/service/DefaultChatServiceTest.java` | 신규 | 서비스 정책·권한·읽음 단위 테스트 | 원본 `ChatServiceTest.java` 확장 |
| `chat/repository/*Test.java` | 신규 | H2 영속성·Flyway 적용 검증 | 신규 |
| `docs/chat/namespace-architecture.md` | 수정 | 실제 구현·활성화·연동 위치 | v1.2 명세 |

## 3. API 상태

| 기능 | 실제 Method·URI | 코드 구현 | 실제 DB 연결 | 기본 테스트 | 남은 연결 |
|---|---|---|---|---|---|
| 방 생성·재사용 | POST `/api/v1/market/listings/{listingId}/chat-rooms` | 완료 | 채팅방 저장 완료 | 통과(계층별) | 실제 판매글 Reader |
| 내 방 목록 | GET `/api/v1/chat-rooms` | 완료 | 방·최근 메시지·읽음 벌크 조회 완료 | 통과(계층별) | 실제 제목·상태·닉네임 Reader |
| 메시지 목록 | GET `/api/v1/chat-rooms/{roomId}/messages` | 완료 | DB 페이지 조회 완료 | 통과 | 실제 HTTP/JWT |
| 메시지 전송 | POST `/api/v1/chat-rooms/{roomId}/messages` | 완료 | DB 저장 완료 | 통과 | 실제 HTTP/JWT |
| 본인 확인 위치 | PUT `/api/v1/chat-rooms/{roomId}/read-position` | 완료 | 사용자별 upsert 완료 | 통과 | 실제 HTTP/JWT |

## 4. 실제 검증

| 체크리스트 ID | 명령·테스트 경로 | 실구현 / Mock | 상태 | 근거·오류 |
|---|---|---|---|---|
| F01 | `gradlew test --tests org.example.springtestci.chat.*` | 혼합 | 통과 | 채팅 31개·전체 54개, 실패·오류·건너뜀 0 |
| F02 | `ChatMigrationTest` | 실구현 | 통과 | 격리 H2에 V1~V2 적용, 채팅 테이블 3개 확인 |
| F03 | Service·Repository 테스트 | 계층별 | 통과 | 생성 조건과 방 저장을 각각 확인. 실제 HTTP 연동은 대기 |
| F04 | `DefaultChatServiceTest` | Mock | 통과(Mock) | 동일 listing+inquirer 기존 방 반환과 신규 생성 미호출 확인 |
| F05 | `DefaultChatServiceTest` | Mock | 부분 통과 | 문의자 목록 DTO·상대 정보·판매글 상태 확인. 양측 실제 목록은 대기 |
| F06 | Service·Repository 테스트 | 계층별 | 통과 | 인증 사용자를 발신자로 저장하고 재조회 |
| F07 | Repository 테스트 | 실구현 | 부분 통과 | 판매자 발신 저장 확인. 실제 판매자 HTTP→구매자 조회는 대기 |
| F08 | `ChatRepositoryIntegrationTest` | 실구현 | 통과 | 메시지 3개를 size=2의 첫/다음 페이지로 조회, hasNext·중복·누락 확인 |
| F09 | Service·Repository 테스트 | 계층별 | 통과 | 실제 방별 최신 메시지 쿼리와 목록 DTO 매핑을 각각 확인 |
| F10 | `ChatRepositoryIntegrationTest` | 실구현 | 통과 | 본인 위치 저장 후 unread 해제 확인 |
| F11 | `ChatRepositoryIntegrationTest` | 실구현 | 통과 | 확인 위치 갱신 뒤 상대 새 메시지 저장 시 unread 재등장 확인 |
| F12 | `ChatRepositoryIntegrationTest` | 실구현 | 통과 | flush/clear 후 방·메시지·확인 위치 재조회 |
| G01 | 실제 SecurityFilterChain HTTP 테스트 | 미실행 | 차단 | 판매글·회원 Reader 빈 미연결로 API 비활성 |
| G02 | `DefaultChatServiceTest` | Mock | 통과(Mock) | 비참여자의 목록·전송·확인 위치 변경 모두 거부, 저장 미호출 확인 |
| G03 | `ChatAccessPolicyTest` | 단위 | 통과 | 자기 판매글 신규 방 차단 |
| G04 | `ChatAccessPolicyTest` | 단위 | 통과 | RESERVED·SOLD·CANCELLED 신규 방 차단 |
| G05 | `DefaultChatServiceTest` | Mock | 통과(Mock) | 비활성 상태별 기존 방 재사용·조회·전송 유지 확인 |
| G06 | 정책 테스트 | 단위 | 부분 통과 | 고정 참여자 접근은 확인. 실제 소유권 변경 fixture 연동은 대기 |
| G07 | Service·Controller 테스트 | 혼합 | 통과 | 공백 전송과 다른 방 메시지 확인 위치 지정 거부 |
| G08 | `ChatControllerTest` | Mock | 부분 통과 | 인증 principal 전달 확인. 실제 Bearer JWT는 미실행 |
| I01 | 실제 HTTP→Service→PostgreSQL | 미실행 | 차단 | 공유 판매글·회원 모듈 미구현 |
| I02 | DTO·로그·의존성 코드 검토 | 실구현 | 통과 | 본문 로그 차단, 상대 읽음 비공개 |
| I03 | 변경 파일·의존성 검토 | 실구현 | 통과 | 주문·견적·파일·STOMP·원본 보안 설정 없음 |
| I04 | 이 문서 | 실구현 | 통과 | 구현·연동·검증 상태와 다음 위치 기록 |

## 5. 기존 정의와 잠정 선택

| 항목 | 발견한 기존 정의·출처 | 이번 선택 | 구분 | 나중에 바꿀 위치 |
|---|---|---|---|---|
| URI·성공 코드 | 기존 `ChatController` 5개 경로와 200 응답 | 그대로 유지 | 기존값 | API 확정 시 Controller |
| 목록 정렬·페이지 | page 0, size 20, 최대 100 | 방은 최근 활동순, 메시지는 생성시각·ID 내림차순 | 잠정안 | JPA 쿼리·Service sort |
| 확인 위치·새 메시지 | 기존 DTO·Repository 계약만 존재 | 확인 위치 레코드가 없으면 미확인, 이후 상대 메시지 존재 여부 | 잠정안 | Message 쿼리·Service |
| 텍스트 입력 | `@NotBlank`, 길이 미정 | 공백 거부, 원문 그대로 저장, 길이 제한 없음 | 잠정안 | 요청 DTO·Migration |

### 공통 보안·응답 차이

| 항목 | v1.2 이관 기준 | 현재 공통 구현·출처 | 이번 선택 | 승인 상태·검증 |
|---|---|---|---|---|
| 인증 | HttpOnly 쿠키의 Access/Refresh Token | `SecurityConfig`: Bearer JWT Resource Server | 공유 구현 유지 | 실제 JWT HTTP 검증 대기 |
| CSRF | `X-CSRF-Token` | `SecurityConfig`: CSRF 비활성 | 공유 설정 미변경 | 공통 보안 승인 확인 필요 |
| 응답 | 단건 `data`, 목록 `data+page` | `ApiResponse`, `ChatPageResponse`: `success+data.items+data.page` | 기존 골격 유지 | Controller Mock 검증 |
| 성공 코드 | 기존 정의 우선, 부재 시 201/204 잠정안 | 기존 Controller 200 | 기존 정의 우선 | API 확정 시 재검토 |

## 6. 남은 일

| 항목 | 이유 | 영향받는 기능 | 필요한 연결 | 독립적으로 가능한 다음 작업 |
|---|---|---|---|---|
| `ChatListingReader` 구현 | 채팅과 승인된 판매글 조회 어댑터 미연결 | 방 생성, 목록 제목·상태 | listing·product·ownership 모듈 | 확정 Entity 기준 어댑터와 벌크 조회 테스트 작성 |
| `ChatUserReader` 구현 | 회원 Entity·닉네임 정의 없음 | 목록 상대 닉네임 | auth/user 모듈 | 벌크 닉네임 조회 어댑터 작성 |
| 공유 FK 추가 여부 | 공유 테이블 물리명 미확정 | DB 무결성 | users/listings Migration | 후속 신규 Migration으로만 추가 |
| 실제 HTTP·JWT·PostgreSQL 검증 | 위 두 Reader 미연결 | 5개 API 전체 | 개발 fixture와 JWT | 플래그 활성화 후 F03~F11 재실행 |
| 성공 코드·길이 정책 확정 | 기존 골격과 v1.2 잠정안 차이 | 생성·전송 API | API 승인값 | Controller·DTO·DDL 조정 |

고급 경합, 재전송, 부하, 장애 복구, 실시간 전파와 운영 배포는 이번 범위에서 제외했다.

검증 과정의 `spotlessCheck`는 새 파일 줄바꿈과 테스트 체이닝 포맷을 지적했다. `spotlessApply` 후 최종 실행한 `spotlessCheck`, 전체 `test`, `build`는 모두 성공했다.

## 7. 다음 Codex 실행용 인계

```text
현재 브랜치와 변경 파일을 먼저 확인한다.
완료한 부분: DefaultChatService, 채팅 JPA 어댑터, V2 Migration, 채팅 테스트.
실패한 테스트와 재현 명령: 없음. 채팅 테스트 명령은 문서의 검증 명령 참조.
다음 구현 위치: listing 모듈의 ChatListingReader, user 모듈의 ChatUserReader.
추가로 필요한 외부 연결: 확정된 판매글·제품 소유권·회원 Entity와 fixture.
미결이지만 다른 작업을 막지 않는 항목: 성공 코드, 메시지 최대 길이, 공유 FK.
이전 구현·팀원 변경을 덮어쓰지 않고 위 지점부터 계속한다.
```
