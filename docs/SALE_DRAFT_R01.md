# R01·R02 판매글 비공개 임시저장과 이어쓰기

현재 소유자가 보유 제품과 사진을 선택하고 작성 중인 판매 내용을 서버에 비공개로 저장한다.
필수 항목이 비어 있어도 저장할 수 있으며, 입력된 값과 사진 소속·소유권은 검사한다.
저장은 사용자가 임시저장을 눌렀을 때만 수행한다.

기준은 [요구사항의 SALE-01·SALE-03](REQUIREMENTS.md)과 [ERD의 listing_drafts·draft_images](ERD.md)다.

## API

모든 경로는 인증이 필요하며 현재 소유자만 사용할 수 있다.
성공 응답은 공통 `ApiResponse` 형식을 사용하고, 초안 및 사진 응답에 `Cache-Control: no-store`를 적용한다.

| Method | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/listing-draft-products` | 보유 중인 제품 목록 |
| GET | `/api/v1/listing-drafts?page=0&size=20` | 내가 작성했고 현재 소유 중인 제품의 저장본 목록 |
| GET | `/api/v1/products/{productId}/listing-draft` | 저장본 상세 조회, 없거나 접근할 수 없으면 404 |
| GET | `/api/v1/products/{productId}/listing-draft/composition` | 판매 화면용 제품 정보·사양·사진 |
| GET | `/api/v1/products/{productId}/listing-draft/images/{imageId}` | 소유자 전용 제품 사진 |
| POST | `/api/v1/products/{productId}/listing-draft` | 처음 저장 201, 저장본이 이미 있으면 409 |
| PUT | `/api/v1/products/{productId}/listing-draft?draftId={draftId}` | 기존 초안 전체 교체 200, 본문에 조회한 version 필수 |
| DELETE | `/api/v1/products/{productId}/listing-draft?draftId={draftId}&version={version}` | 저장본과 사진 선택 삭제 204 |

저장 요청은 `title`, `description`, `price`, `conditionGrade`, `selectedImageIds`, `version`을 받는다.
공백 문자열은 null로 정리하고, 사진 선택을 생략하면 빈 배열로 저장한다.
기존 초안을 저장할 때는 직전 응답의 `version`이 필요하며 충돌하면 기존 저장본을 유지하고 409를 반환한다.
수정·삭제는 조회한 `draftId`도 함께 검사한다. 삭제 후 새 저장본의 version이 다시 0이어도 이전 화면이 새 저장본을 덮어쓰거나 삭제할 수 없다.
POST는 생성 전용이고 기존 저장본의 수정은 PUT으로 보낸다. PUT에서 생략한 선택 필드는 null 또는 빈 사진 배열로 교체한다.

목록은 `data.items`와 `data.page(number,size,totalElements,totalPages,hasNext)`를 반환한다.
기본 20개, 최대 100개이며 수정 시각·초안 번호 내림차순으로 정렬한다. 페이지를 자르기 전에 작성자·현재 소유권·제품 보관 상태를 검사한다.

## 화면 흐름

판매글 작성 화면의 내 임시저장 목록에서 이어쓰거나, 보유 제품을 선택하면 접근 가능한 기존 저장본을 불러온다.
제목·가격·설명·등급·사진 순서·대표 선택과 저장 버전을 복원한다. URL에는 선택 제품 번호만 기록해 새로고침 후에도 같은 제품을 다시 연다.
본문과 인증 정보는 브라우저 저장소에 보관하지 않는다.

수정·삭제 충돌 시 작성 중인 내용을 유지하고 저장본 다시 불러오기를 제공한다. 다시 불러오거나 저장하지 않은 내용이 있는 제품을 변경할 때는 확인한다.
삭제는 사용자 확인 후 수행하고, 선택 중이던 저장본을 삭제하면 해당 제품의 빈 작성 화면으로 돌아간다.

현재 입력 제한은 제목 60자, 설명 2,000자, 사진 20장이다. 가격은 1원 이상 12자리 이내의 정수,
상태 등급은 1~5다. 사진 배열의 첫 번째 항목이 대표 사진이다.

## 제품 조회와 저장 구조

- 판매 폼의 제품 기본 정보·사양·사진은 `ProductService`에서 제공하는 값을 사용하며 현재 사양을 별도로 계산하지 않는다.
- 저장 목록은 제품명과 현재 소유권을 한 번에 검사하는 조회를 사용한다. 개수 조회와 제한된 목록 조회로 처리하며 본문·사진 컬렉션을 읽지 않는다.
- 판매용 조회는 `getForListing`을 사용해 시리얼을 조회·복호화하지 않는다. 사진 제공은 제품 서비스의 기존 `image` 기능을 재사용한다.
- 판매 화면의 DTO에는 필요한 필드만 복사한다. 소유자용 제품 응답에 개인정보가 추가되더라도 시리얼·증빙·저장소 키를 그대로 전달하지 않는다.
- 수정은 제품 서비스의 소유권 검사·제품 행 잠금 → 초안 작성자·버전 검사 → 사진 소속 검사 → 내용과 사진 선택 저장 순서로 같은 트랜잭션에서 처리한다.
- `draft_images`에는 `draft_id`, `product_image_id`, `sort_order`, `is_primary`를 저장한다. 사진 중복·순서 중복·복수 대표를 막고 실제 초안·제품 사진을 외래키로 참조한다.
- 초안을 삭제하면 사진 선택 관계도 삭제한다. 제품 화면의 사진 순서·대표 선택과 사진 파일은 변경하지 않는다.
- 제품 사진을 삭제하면 해당 사진의 초안 선택도 정리하고 남은 선택의 순서·대표 사진과 초안 버전을 갱신한다. 이전 화면에서 저장한 버전으로 덮어쓰는 요청은 충돌로 처리한다.
- `V2026102002__align_listing_draft_images.sql`은 기존 `listing_draft_images`의 사진 순서와 대표 선택을 새 구조로 옮긴다. 기존 초안의 내용과 버전은 유지한다.
- 초안의 작성자·버전·PRIVATE 제한은 접근 제어와 저장 충돌 방지를 위해 유지한다.

## 연동 범위

제품의 DB 이름 변경과 이력을 반영한 현재 사양 계산은 제품·이력 기능에서 처리한다.
현재 main의 제품 조회는 아직 `laptop_details`를 읽는다. SALE이 제품 서비스를 사용하도록 연결한 것과,
제품 서비스가 최신 ERD의 계산 규칙까지 구현한 것은 구분해야 한다.

인증은 공통 보안 설정을 따른다. 프런트는 `PastportApi.fetch`를 통해 인증 쿠키와 쓰기 요청의 CSRF 헤더를 전송한다.
CSRF는 공통 인증 모듈에서 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 전달하며, 인증 토큰을 판매 화면에서 직접 읽거나 저장하지 않는다.
파일 저장은 기존 `FileStorage`를 사용한다.

미리보기·게시·소유권 이전에 따른 실제 초안 정리는 후속 구현 범위다. 이전 완료 후 남아 있는 초안은 이전 소유자·새 소유자 모두 조회·수정·삭제할 수 없다.
이번 기능에서는 판매글을 게시하거나 이력을 공개하거나 AI를 호출하지 않는다.

## 검증과 로컬 확인

`ListingDraftHttpTest`는 운영 마이그레이션으로 생성한 테이블에서 실제 HTTP·JWT·제품 서비스·JPA 저장을 검증한다.
제품 등록·사진 업로드 후 초안 작성, 소유권과 사진 검사, 사진 순서·대표 선택, 버전 충돌과 동시 저장을 포함한다.
R02는 저장본 재조회·페이지 구분·권한 필터·수정·삭제와 재생성 후 이전 화면의 요청 거부, 동시 수정·삭제도 검증한다.
`ListingDraftMigrationTest`는 기존 데이터가 있는 DB의 업그레이드 시 초안 내용·버전·사진 선택 보존을 확인한다.
`SALE_DRAFT_TEST_DB_URL/USER/PASSWORD` 환경 변수를 지정하면 두 테스트를 전용 PostgreSQL 테스트 DB에서도 실행한다.

```powershell
./gradlew.bat spotlessCheck test build
```

로컬 화면 확인은 `scripts/run-sale-draft-demo.ps1`을 사용한다.
`local,sale-draft-demo` 프로필은 로컬 샘플 전용이며 배포 환경에서 사용하지 않는다.
