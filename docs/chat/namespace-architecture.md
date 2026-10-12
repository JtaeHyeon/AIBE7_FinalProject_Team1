# 채팅 API 네임스페이스 기반 구조

## 상태

- 작업 기준: `Pastport_P1_Chat_API_Namespace_Architecture_Task_Spec_v1.1.md`
- 구현 범위: TX-01 채팅 시작, TX-02 채팅·거래 진행의 호출 계약과 기반 구조
- 공개 상태: 제안 경로이며 공개 전 확인 필요
- 운영 등록: 기본 비활성. `app.features.chat-api.enabled=true`일 때만 `ChatController`가 등록된다.
- 실제 처리: 미구현. `ChatService` 구현 빈과 저장소 어댑터를 연결하기 전에는 기능 플래그를 활성화하지 않는다.

## 저장소 확인 결과

| 항목 | 확인 결과 |
|---|---|
| 루트 패키지 | `org.example.springtestci` |
| 계층 관례 | 기능 패키지 아래 `controller`, `dto`, `service`, `entity`, `repository` |
| 기존 채팅 구현 | Controller, Entity, Repository, Migration, API 문서 모두 없음 |
| 인증 | Spring Security OAuth2 Resource Server의 Bearer JWT. `Authentication#getName()`에 전달되는 JWT subject를 `long` 사용자 ID로 해석 |
| 공통 응답 | `ApiResponse<T>`의 `{"success": true, "data": ...}` 형식 |
| 오류 처리 | `CommonException`, `ErrorCode`, `GlobalExceptionHandler` 재사용 |
| 페이지네이션 | 공통 구현 없음. 채팅 계약용 `ChatPageResponse<T>`에 `items`와 `page`를 정의 |
| DB | `V1__baseline.sql`만 있고 업무 테이블이 없음. 채팅 Migration은 이번 범위에서 추가하지 않음 |

명세에 이관된 HttpOnly 쿠키 인증과 `X-CSRF-Token` 기준은 현재 코드와 다르다. 현재 보안 설정은 Bearer JWT를 사용하고 CSRF를 비활성화한다. 이번 작업에서는 공유 보안 정책을 변경하지 않았다.

## API 대응표

| 기능 | Method | URI | 요청 DTO | 응답 DTO | Service |
|---|---|---|---|---|---|
| 판매글 대화방 생성 또는 기존 방 반환 | POST | `/api/v1/market/listings/{listingId}/chat-rooms` | 본문 없음 | `ChatRoomResponse` | `openRoom` |
| 내 대화방 목록 | GET | `/api/v1/chat-rooms` | `page`, `size` | `ChatPageResponse<ChatRoomSummaryResponse>` | `listMyRooms` |
| 대화방 메시지 목록 | GET | `/api/v1/chat-rooms/{roomId}/messages` | `page`, `size` | `ChatPageResponse<ChatMessageResponse>` | `listMessages` |
| 텍스트 메시지 전송 | POST | `/api/v1/chat-rooms/{roomId}/messages` | `SendChatMessageRequest` | `ChatMessageResponse` | `sendMessage` |
| 내 마지막 확인 위치 갱신 | PUT | `/api/v1/chat-rooms/{roomId}/read-position` | `UpdateReadPositionRequest` | `ReadPositionResponse` | `updateMyReadPosition` |

목록 기본값은 `page=0`, `size=20`이고 최대 크기는 100이다. 모든 경로의 사용자 식별자는 요청 본문이 아니라 인증 객체에서 가져온다. 생성과 기존 방 재사용을 구분하는 성공 상태 코드, 메시지 정렬 방향, 최종 URI와 DTO 필드명은 공개 전 확정이 필요하다.

## 코드 구조와 책임

| 경로 | 책임 |
|---|---|
| `chat/controller/ChatController.java` | 5개 경로, 요청 검증, 인증 사용자 ID 전달, 공통 응답 래핑 |
| `chat/service/ChatService.java` | 5개 기능의 타입 기반 호출 계약 |
| `chat/service/ChatListingReader.java` | 판매글·판매자·제품·현재 소유자·상태 조회 경계 |
| `chat/domain/ChatAccessPolicy.java` | 신규 방 생성과 기존 방 접근 규칙 |
| `chat/domain/*` | DB에 종속되지 않은 최소 채팅 데이터 의미 |
| `chat/repository/*` | 방·메시지·개인 확인 위치 저장과 조회 계약 |
| `chat/dto/*` | API 입력·출력 계약 |

메시지 본문과 최근 메시지 미리보기를 가진 객체는 문자열 표현에서 내용을 숨긴다. 저장 Entity나 JPA Repository는 물리 스키마 확정 전 등록하지 않았다.

## 정책 경계

- 신규 방은 판매글이 `ACTIVE`이고 최초 판매자와 현재 제품 소유자가 같으며 요청자가 판매자가 아닐 때만 생성 가능하다.
- `RESERVED`, `SOLD`, `CANCELLED` 판매글에서는 신규 방 생성을 허용하지 않는다.
- 기존 방 접근은 현재 제품 소유자가 아니라 방에 고정된 최초 판매자 또는 문의자인지로 판단한다.
- 판매글 상태와 무관한 기존 방 재사용·조회·전송은 후속 Service에서 기존 방 조회를 신규 생성 검증보다 먼저 수행해야 한다.
- `(listingId, inquirerId)` 중복 방 방지는 Repository 계약에 드러나지만 UNIQUE 제약과 동시성 처리는 스키마 확정 후 구현한다.

## 후속 구현

1. `listing`, `product`, `user` 모델 확정 후 `ChatListingReader` 어댑터를 연결한다.
2. 채팅 테이블명, FK, 삭제 정책, 인덱스와 `(listing_id, inquirer_id)` UNIQUE 제약을 확정하고 새 Flyway Migration을 추가한다.
3. `ChatService` 구현에서 트랜잭션, 중복 생성 경합, 참여자 권한, 메시지 저장, 읽음 위치 역행 방지를 처리한다.
4. 목록 정렬·커서 또는 페이지 방식, 메시지 길이·공백 정책, 생성/재사용 상태 코드를 API 명세로 확정한다.
5. 인증 쿠키·CSRF 정책과 현재 Bearer JWT 설정의 차이를 팀 보안 기준에 맞춰 해소한다.
6. Service·Repository 통합 테스트와 DB 동시성 테스트를 추가한 뒤 기능 플래그 활성화를 검토한다.

## 검증 명령

```powershell
.\gradlew.bat test --tests "org.example.springtestci.chat.*"
.\gradlew.bat spotlessCheck
.\gradlew.bat test
.\gradlew.bat build
```

채팅 단위·MVC 계약 테스트는 신규 방 상태 규칙, 고정 참여자 접근, 5개 매핑, 인증 사용자 전달, 요청 바인딩과 응답 직렬화를 검증한다.

## 검증 결과

- 2026-10-12: 채팅 테스트 13개 성공 (`ChatControllerTest` 6개, `ChatAccessPolicyTest` 7개)
- 2026-10-12: 전체 테스트 18개 성공, 실패·오류·건너뜀 0개
- 2026-10-12: `spotlessCheck`, `test`, `build` 연속 실행 성공
