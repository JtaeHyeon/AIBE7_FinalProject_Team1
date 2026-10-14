# 채팅 API 네임스페이스·기본 구현

## 상태

- 작업 기준: `Pastport_Chat_Codex_All_In_One_v1.2.md`
- 구현 범위: TX-01 채팅 시작, TX-02 채팅·거래 진행 중 기본 채팅 API 5개
- 운영 등록: 기본 비활성. `app.features.chat-api.enabled=true`일 때 Controller와 Service가 등록된다.
- 외부 연결: 판매글 모듈의 `ChatListingReader`, 회원 모듈의 `ChatUserReader` 구현 빈이 필요하다.
- 제외 범위: 화면, 파일, STOMP/WebSocket, 주문·견적, 거래 상태 변경, 운영 배포

## API 대응표

| 기능 | Method | URI | 요청 | 응답 | Service |
|---|---|---|---|---|---|
| 방 생성 또는 기존 방 반환 | POST | `/api/v1/market/listings/{listingId}/chat-rooms` | 본문 없음 | `ChatRoomResponse` | `openRoom` |
| 내 방 목록 | GET | `/api/v1/chat-rooms` | `page`, `size` | `ChatPageResponse<ChatRoomSummaryResponse>` | `listMyRooms` |
| 메시지 목록 | GET | `/api/v1/chat-rooms/{roomId}/messages` | `page`, `size` | `ChatPageResponse<ChatMessageResponse>` | `listMessages` |
| 텍스트 메시지 전송 | POST | `/api/v1/chat-rooms/{roomId}/messages` | `SendChatMessageRequest` | `ChatMessageResponse` | `sendMessage` |
| 내 확인 위치 갱신 | PUT | `/api/v1/chat-rooms/{roomId}/read-position` | `UpdateReadPositionRequest` | `ReadPositionResponse` | `updateMyReadPosition` |

기존 골격의 성공 응답 형식과 상태 코드를 유지한다. 모든 성공 응답은 `ApiResponse<T>`의 `{"success": true, "data": ...}` 형식이며 현재 Controller는 200을 반환한다. 목록 기본값은 `page=0`, `size=20`, 최대 크기는 100이다. 메시지는 `createdAt`, `id` 내림차순으로 DB 페이지 조회한다.

## 구현 구조

| 경로 | 책임 |
|---|---|
| `chat/controller/ChatController.java` | 5개 HTTP 경로, 입력 검증, 인증 사용자 ID 전달 |
| `chat/service/DefaultChatService.java` | 방 재사용·생성, 참여자 인가, 메시지·확인 위치 처리 |
| `chat/service/ChatListingReader.java` | 판매글·판매자·제품 소유자·상태·제목 조회 경계 |
| `chat/service/ChatUserReader.java` | 상대 사용자 닉네임 벌크 조회 경계 |
| `chat/repository/jpa/*` | 채팅방·메시지·확인 위치 JPA 저장 어댑터 |
| `db/migration/V2__create_chat_tables.sql` | 채팅 전용 테이블·제약·인덱스 |

공유 사용자·제품·판매글 테이블의 물리 스키마가 아직 없으므로 `seller_id`, `inquirer_id`, `listing_id`에는 외부 FK를 임의로 추가하지 않았다. 채팅 내부의 메시지·확인 위치 FK와 `(listing_id, inquirer_id)`, `(room_id, user_id)` UNIQUE 제약은 적용했다.

## 정책

- 기존 방 조회는 현재 판매글 상태 검사보다 먼저 수행한다.
- 신규 방은 `ACTIVE`, 최초 판매자와 현재 제품 소유자 일치, 타인 판매글 조건에서만 생성한다.
- 판매자와 최초 문의자 사용자 ID를 고정 참여자로 사용한다.
- 기존 방 조회·전송·확인 위치 변경은 고정 참여자만 가능하다.
- 발신자와 확인 위치 소유자는 요청 본문이 아니라 인증 사용자로 결정한다.
- 확인 위치는 같은 방에 실제 존재하는 메시지만 지정할 수 있다.
- 새 메시지는 본인의 확인 위치 이후에 저장된 상대 발신 메시지로 계산한다.
- 메시지 본문은 응답 이외 로그와 문자열 표현에서 가린다.

## 외부 모듈 연결 위치

1. 판매글 구현에서 `ChatListingReader`를 구현해 `listingId`, 최초 판매자, 제품, 현재 소유자, 상태, 제목을 반환한다.
2. 회원 구현에서 `ChatUserReader`를 구현해 사용자 ID별 닉네임을 벌크 반환한다.
3. 두 빈이 준비된 환경에서 `app.features.chat-api.enabled=true`로 설정한다.
4. 실제 Bearer JWT와 개발 PostgreSQL로 HTTP 기본 흐름을 재검증한다.

## 검증 명령

```powershell
.\gradlew.bat test --tests "org.example.springtestci.chat.*"
.\gradlew.bat spotlessCheck
.\gradlew.bat test
.\gradlew.bat build
```

상세 결과와 미연결 항목은 `docs/chat/basic-implementation-result-v1.2.md`에 기록한다.
