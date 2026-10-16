# R01 판매글 비공개 임시저장

기준일: 2026-10-12. 브랜치: `feature/sale-draft-r01`.

## 확인한 요구사항

[WBS](https://app.notion.com/p/3f23550b7b55802ca75ceaff66e09a52?v=3f23550b7b5580d49e84000c1add14a1)에서 아래 세 항목의 담당자는 [AIBE7]정태현이다. 항목 본문은 기본 템플릿이고 별도 완료 기준은 없었다.

| 항목 | 이번 구현 |
| --- | --- |
| [R01-1](https://app.notion.com/p/3f73550b7b5580b2b16beca2eb59ceb9) | 새 Flyway migration, 비공개 초안·선택 사진 순서 저장, 저장 API |
| [R01-2](https://app.notion.com/p/3f73550b7b55806390d7c4a41c676a16) | 기존 sell.html과 공통 스타일에 보유 제품·사양·사진 조회, 등급 선택, 대표·사진 순서 변경, 임시저장 연결 |
| [R01-3](https://app.notion.com/p/3f73550b7b55809db919fb616e7b354f) | 부분 입력 허용, 입력값·현재 소유권·사진 소속 검사, 비공개 강제, 저장 충돌 방어 |

[요구사항 정의서](https://app.notion.com/p/3e93550b7b5580a5ab7bcc03da1a0e85)의 실제 본문은 v2.0, 최종 수정일 2026-10-11이다. 프로젝트 문서의 링크 이름은 v1.4로 남아 있다. 이번 동작은 SALE-01·03 및 입력 검증 정책을 따른다.

[API 명세](https://app.notion.com/p/3e93550b7b5580cc9222fe0d32e33c92)의 초안 구조는 Review 상태다. 저장 경로와 필드는 이를 기준으로 정하고, R01에서 필요한 반복 저장은 같은 POST 경로에 version을 보내도록 구체화했다. [ERD](https://app.notion.com/p/3e33550b7b5580529386f7144fd04cd6)의 products/laptop_details/product_images 컬럼을 읽기 계약으로 사용한다.

## API 계약

모든 경로는 인증이 필요하다. 성공 응답은 기존 공통 `{success:true,data:...}` 형식을 사용한다. DTO만 반환하고 저장소 키·작성자·시리얼·증빙은 반환하지 않는다. 응답과 제품 이미지에 `Cache-Control: no-store`를 적용한다.

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | /api/v1/listing-draft-products | 현재 사용자의 보유·미보관 노트북 목록 |
| GET | /api/v1/products/{productId}/listing-draft/composition | 소유자 전용 제품 기본 정보·허용된 사양·제품 사진 |
| GET | /api/v1/products/{productId}/listing-draft/images/{imageId} | 현재 소유권을 검사한 제품 사진 제공 |
| POST | /api/v1/products/{productId}/listing-draft | 새 초안 201 / 올바른 version의 반복 저장 200 |

생성 예시:

```json
{
  "title": "MacBook Pro 판매합니다",
  "description": null,
  "price": 2150000,
  "conditionGrade": 4,
  "selectedImageIds": [303, 301],
  "version": null
}
```

제품과 작성자는 경로 및 서버가 검증한 로그인 사용자로 정한다. 제품당 초안은 하나이고 선택 사진 배열의 첫 항목이 대표다. 같은 화면에서 재저장할 때는 직전 응답의 version을 보내며 내용 전체를 교체한다. 생략하거나 null인 선택 필드는 비어 있는 값으로 저장하고, selectedImageIds는 항상 배열로 응답한다. 현재 저장본이 있는데 version이 없거나 오래되면 409로 기존 내용을 보존한다.

title 60자, description 2,000자, 가격 1~999,999,999,999원의 정수, 상태 등급 1~5, 사진 최대 20장으로 제한한다. 제목·설명·가격·등급·사진은 임시저장 시 생략할 수 있다. 공백 문자열은 null로 정규화한다. 다른 제품·삭제된 사진·중복 사진·null 사진 ID는 거절한다. 증빙 테이블에서 사진을 읽지 않는다.

저장은 products 행 잠금 → 사진 소속 재검사 → 초안 version 검사 → 내용·순서 저장 순서의 단일 트랜잭션이다. 초안은 PRIVATE만 허용하는 DB CHECK를 갖는다. 판매글 생성, 제품 이력 공개, AI 호출, 자동 저장은 수행하지 않는다.

## 팀 연동 지점

현재 main에는 제품·인증 도메인의 package-info와 공통 기반만 있다. 실제 팀 로그인·제품 등록 기능이 완료된 것은 아니다.

- PROD: DraftProductRepository가 ERD의 공유 테이블을 읽는다. shared schema가 다르면 이 어댑터를 맞춘다. 소유권 변경·제품 보관·사진 변경도 products 행을 먼저 잠그는 규칙을 따라야 한다. 운영 migration에 샘플 제품 테이블은 포함하지 않았다.
- AUTH: 기존 SecurityConfig의 Bearer JWT 검증을 사용하며 숫자 subject를 회원 ID로 해석한다. 문서의 HttpOnly 쿠키·CSRF 인증은 AUTH의 resolver/필터가 연결되어야 한다. 프런트는 credentials를 전송하고 CSRF meta 태그가 있으면 쓰기 요청에 헤더를 붙인다. 공통 인증 fetch가 필요하면 `window.PastportApi.fetch(url, options)`를 제공한다. 토큰 입력 UI·브라우저 저장소 보관은 추가하지 않았다.
- STORAGE: 제품 사진은 기존 FileStorage로 권한 검사 후 읽는다. 운영용 FileStorage 구현은 해당 인프라 파트가 연결해야 한다.
- DB: 현재 공유 제품·회원 테이블이 없어 초안의 product_id/author_id/image_id에 해당 외래키를 아직 추가하지 않았다. 소속은 제품 잠금 하에서 검사한다. 공유 migration이 올라오면 새 Flyway migration으로 해당 FK를 추가한다. draft_id FK는 이번 migration에 포함했다.
- R02: 저장본 목록·단건 조회, 새로고침 후 이어쓰기, 별도 수정/삭제 API, 소유권 이전 시 초안 정리는 다음 범위다. 이전 소유자는 현재 소유권 검사를 통과하지 못하고 새 소유자에게 이전 작성 내용을 반환하지 않는다. 이전 hook이 초안을 지우기 전에는 새 소유자의 생성 요청도 409다.
- R03~R05/R08 이후: 공개 예정 이력, 미리보기·게시 및 AI는 아직 연결하지 않았다. 화면에 준비 상태를 표시한다.

## 실행

JDK 17을 사용한다.

```powershell
.\scripts\run-sale-draft-demo.ps1
```

http://127.0.0.1:18081 에서 **내 노트북 팔기**를 누른다. `local,sale-draft-demo`를 명시한 로컬 확인 환경으로, loopback 요청에만 샘플 소유자 1을 사용한다. 이 프로필은 배포에 사용하지 않는다. 샘플 사진은 레포에 있는 기존 이미지다.

데모 DB는 java.io.tmpdir 아래 `pastport-sale-draft-r01` H2 파일이며 서버를 재시작해도 저장된다. R02 조회가 아직 없으므로 새로고침 후 기존 초안을 불러오는 기능은 다음 작업에 포함된다. 일반 실행에서 제품 테이블이 없으면 503으로 실패하고 샘플 데이터로 대체하지 않는다.

## 검증

2026-10-12: PostgreSQL 17.11의 전용 테스트 DB 내 격리 schema로 실제 HTTP·서명된 JWT·Flyway·JPA 저장을 검증했다. 새 SALE 테스트 18개와 기존 5개를 포함한 23개 테스트, 실패 0·건너뜀 0. `spotlessCheck test build` 통과. 테스트 schema는 종료 시 정리한다.

검증 범위: 빈 초안/선택 필드, 가격·등급·길이·사진 제한, 타인·보관 제품 차단, 서버 작성자·비공개 강제, 제품 사진 제공 권한, 사진 순서 저장, version 충돌, 동시 생성/수정, 소유권 변경 후 접근 차단, DB PRIVATE 제약. JS 문법과 PowerShell 구문도 검사했다.

브라우저: 기존 내비게이션에서 작성 화면 진입, 제품·사양·사진 로딩, 등급 선택, 대표 사진 변경, 사진 순서 저장, 정상 저장·반복 저장, 잘못된 가격 거절, 빈 항목 저장을 확인했다. 데스크톱과 390px 모바일에서 본문 가로 넘침이 없고 브라우저 오류가 없었다.

```powershell
# 현재 컴퓨터에 이미 저장된 DPAPI 테스트 접속정보 사용. 값을 출력하지 않는다.
.\scripts\test-sale-draft.ps1 -UseLocalTestDatabase
# PostgreSQL 없이 로컬 호환성을 확인할 때
.\scripts\test-sale-draft.ps1 -UseH2
# 다른 전용 PostgreSQL을 쓰면 SALE_DRAFT_TEST_DB_URL/USER/PASSWORD 설정 후
.\scripts\test-sale-draft.ps1
```

외부 AI 호출, 원격 push/merge 및 실제 팀의 AUTH/PROD 서비스와 통합은 실행하지 않았다.
