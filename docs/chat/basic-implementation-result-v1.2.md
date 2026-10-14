# 채팅 기본 구현 결과

상태: 부분 완료 / 실행일: 2026-10-14 / 대상 저장소: Pastport / 브랜치: `feat/chat-api-namespace`

## 1. 이번 실행

완료한 단계: 기존 골격 분석, 채팅 Service·JPA 저장 어댑터·V2 Migration 구현, 단위·H2 통합 검증

다음에 시작할 단계: 실제 판매글·회원 모듈의 조회 어댑터 연결 후 기능 플래그 활성화와 JWT·PostgreSQL HTTP 통합 검증

실행 환경과 외부 모듈의 실제/Fake 여부: Java 17, H2 PostgreSQL 모드. 채팅 저장소는 실제 JPA/H2를 사용했고 Service의 판매글·회원 경계는 Mockito를 사용했다. 일반 실행용 Fake는 추가하지 않았다.

## 2. 변경 파일

| 경로 | 구분 | 역할 | 참고 원본 파일 |
|---|---|---|---|
| `chat/service/DefaultChatService.java` | 신규 | 5개 기능, 권한·상태·저장 흐름 | `ChatService.java`, `ChatMessageService.java` |
| `chat/service/ChatListingReader.java` | 수정 | 판매글 벌크 조회 경계 | 원본 의존성 제거 후 Pastport 경계 유지 |
| `chat/service/ChatUserReader.java` | 신규 | 닉네임 벌크 조회 경계 | 원본 회원 조회 패턴만 참고 |
| `chat/repository/ChatMessageRepository.java` | 수정 | 최신 메시지·새 메시지 조회 계약 | `ChatMessageRepository.java` |
| `chat/repository/jpa/*` | 신규 | 방·메시지·확인 위치 실제 JPA 어댑터 | 원본 저장·최근 메시지 쿼리 선별 이식 |
| `db/migration/V2__create_chat_tables.sql` | 신규 | 채팅 테이블·내부 FK·UNIQUE·인덱스 | Pastport 전용 신규 스키마 |
| `chat/service/DefaultChatServiceTest.java` | 신규 | 서비스 정책·권한·읽음 단위 테스트 | 원본 `ChatServiceTest.java` 확장 |
| `chat/repository/*Test.java` | 신규 | H2 영속성·Flyway 적용 검증 | 신규 |
| `docs/chat/namespace-architecture.md` | 수정 | 실제 구현·활성화·연동 위치 | v1.2 명세 |

## 3. API 상태

| 기능 | 실제 Method·URI | 코드 구현 | 실제 DB 연결 | 기본 테스트 | 남은 연결 |
|---|---|---|---|---|---|
| 방 생성·재사용 | POST `/api/v1/market/listings/{listingId}/chat-rooms` | 완료 | 채팅방 저장 완료 | 통과(계층별) | 실제 판매글 Reader |
| 내 방 목록 | GET `/api/v1/chat-rooms` | 완료 | 방·최근 메시지·읽음 벌크 조회 완료 | 통과(계층별) | 실제 제목·닉네임 Reader |
| 메시지 목록 | GET `/api/v1/chat-rooms/{roomId}/messages` | 완료 | DB 페이지 조회 완료 | 통과 | 실제 HTTP/JWT |
| 메시지 전송 | POST `/api/v1/chat-rooms/{roomId}/messages` | 완료 | DB 저장 완료 | 통과 | 실제 HTTP/JWT |
| 본인 확인 위치 | PUT `/api/v1/chat-rooms/{roomId}/read-position` | 완료 | 사용자별 upsert 완료 | 통과 | 실제 HTTP/JWT |

## 4. 실제 검증

| 체크리스트 ID | 명령·테스트 경로 | 실구현 / Mock | 상태 | 근거·오류 |
|---|---|---|---|---|
| F01 | `gradlew test --tests org.example.springtestci.chat.*` | 혼합 | 통과 | 채팅 테스트 26개, 실패 0; 전체 테스트 31개, 실패 0 |
| F02 | `ChatMigrationTest` | 실구현 | 통과 | 격리 H2에 V1~V2 적용, 채팅 테이블 3개 확인 |
| F03~F04 | `DefaultChatServiceTest`, `ChatRepositoryIntegrationTest` | 계층별 | 통과 | 생성·동일 조합 재사용과 저장 확인 |
| F05 | `DefaultChatServiceTest` | Mock | 통과(Mock) | 양 참여자 정책, 상대 정보·목록 DTO 확인 |
| F06~F07 | `DefaultChatServiceTest`, `ChatRepositoryIntegrationTest` | 계층별 | 통과 | 서버 발신자와 메시지 저장·재조회 확인 |
| F08~F09 | Service·Repository·Controller 테스트 | 혼합 | 통과 | DB 페이지·최신 메시지·목록 응답 확인 |
| F10~F11 | Service·Repository 테스트 | 혼합 | 통과 | 개인 위치 저장과 상대 새 메시지 계산 확인 |
| F12 | `ChatRepositoryIntegrationTest` | 실구현 | 통과 | 실제 EntityManager·H2 재조회 확인 |
| G01 | 실제 SecurityFilterChain HTTP 테스트 | 미실행 | 차단 | 판매글·회원 Reader 빈 미연결로 API 비활성 |
| G02~G07 | 정책·Service·Controller 테스트 | 혼합 | 통과 | 비참여자, 자기 글, 상태, 소유권, 입력·대상 검증 |
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
| 확인 위치·새 메시지 | 기존 DTO·Repository 계약만 존재 | 위치 없음은 0, 이후 상대 메시지 존재 여부 | 잠정안 | Message 쿼리·Service |
| 텍스트 입력 | `@NotBlank`, 길이 미정 | 공백 거부, 원문 그대로 저장, 길이 제한 없음 | 잠정안 | 요청 DTO·Migration |

## 6. 남은 일

| 항목 | 이유 | 영향받는 기능 | 필요한 연결 | 독립적으로 가능한 다음 작업 |
|---|---|---|---|---|
| `ChatListingReader` 구현 | 판매글 Entity·물리 스키마 없음 | 방 생성, 목록 제목 | listing·product·ownership 모듈 | 확정 Entity 기준 어댑터와 테스트 작성 |
| `ChatUserReader` 구현 | 회원 Entity·닉네임 정의 없음 | 목록 상대 닉네임 | auth/user 모듈 | 벌크 닉네임 조회 어댑터 작성 |
| 공유 FK 추가 여부 | 공유 테이블 물리명 미확정 | DB 무결성 | users/listings Migration | 후속 신규 Migration으로만 추가 |
| 실제 HTTP·JWT·PostgreSQL 검증 | 위 두 Reader 미연결 | 5개 API 전체 | 개발 fixture와 JWT | 플래그 활성화 후 F03~F11 재실행 |
| 성공 코드·길이 정책 확정 | 기존 골격과 v1.2 잠정안 차이 | 생성·전송 API | API 승인값 | Controller·DTO·DDL 조정 |

고급 경합, 재전송, 부하, 장애 복구, 실시간 전파와 운영 배포는 이번 범위에서 제외했다.

검증 중 최초 `spotlessCheck`는 새 Java 파일의 줄바꿈 형식 차이로 실패했다. `spotlessApply` 후 재실행한 `spotlessCheck`, 전체 `test`, `build`는 모두 성공했다.

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
