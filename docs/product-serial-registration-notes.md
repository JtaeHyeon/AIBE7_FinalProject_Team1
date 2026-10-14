# 시리얼 보호·제품 등록 작업 정리

작성일: 2026-10-14

## 1. 작업 목표

인증된 사용자가 노트북을 등록할 수 있는 API를 구현한다.

제품 등록 과정에서는 다음 조건을 만족해야 한다.

- 인증된 사용자를 제품의 최초 소유자로 지정한다.
- 제조사와 시리얼 번호를 정규화한다.
- 동일한 실물 제품의 중복 등록을 막는다.
- 시리얼 번호 원문은 DB와 API 응답에 남기지 않는다.
- 제품 공통 정보와 노트북 상세 사양을 한 트랜잭션으로 저장한다.

## 2. 요구사항 해석과 설계 결정

### 2.1 ERD와 WBS의 차이

ERD에는 정규화된 `serial_number`를 저장하고 `(manufacturer, serial_number)` 조합으로 중복을 막도록 기술되어 있다.

반면 WBS의 보안 완료 조건은 다음과 같다.

- 시리얼 원문 미저장
- 시리얼 지문과 마지막 4자리만 저장
- 중복 등록 차단

두 문서가 충돌하므로 더 엄격한 보안 조건인 WBS를 적용했다. 결과적으로 DB에는 다음 값만 저장한다.

| 값 | 용도 | 공개 여부 |
|---|---|---|
| `serial_number_fingerprint` | 중복 제품 식별 | 비공개 |
| `serial_last4` | 내부 식별 보조 | 현재 API에서는 반환하지 않음 |
| 시리얼 원문 | 저장하지 않음 | 반환하지 않음 |

### 2.2 단순 해시 대신 HMAC을 사용한 이유

일반 SHA-256 해시는 같은 입력에 항상 같은 결과를 만든다. 시리얼 번호의 형식이나 후보 범위가 알려지면 공격자가 예상값을 반복해서 해시하여 원문을 추측할 수 있다.

HMAC-SHA256은 서버가 가진 비밀키를 함께 사용한다. 비밀키를 모르면 같은 지문을 재현하기 어렵기 때문에 단순 해시보다 사전 대입 공격에 강하다.

지문 입력에는 제조사와 시리얼을 함께 사용한다.

```text
HMAC(secret, normalizedManufacturer + separator + normalizedSerialNumber)
```

따라서 서로 다른 제조사에서 같은 문자열의 시리얼을 사용해도 다른 지문이 만들어진다.

### 2.3 정규화 규칙

현재 공통 정규화 규칙은 다음과 같다.

1. Unicode NFKC 정규화
2. 공백과 하이픈 제거
3. 영문 대문자 변환
4. 길이 검증

예를 들어 아래 입력은 모두 같은 시리얼로 처리된다.

```text
ab-12 34
AB1234
Ab 12-34
```

현재 규칙은 MVP용 공통 규칙이다. 제조사마다 허용 문자와 체크섬 규칙이 다를 수 있으므로 제조사별 정책이 확정되면 확장해야 한다.

## 3. 구현 구조

요청은 프로젝트의 기본 계층 구조를 따른다.

```text
POST /api/products
        ↓
ProductController
        ↓
ProductRegistrationService
        ↓
ProductRepository + LaptopDetailRepository
        ↓
products + laptop_details
```

### 3.1 Controller

`ProductController`는 다음 역할만 담당한다.

- 요청 DTO 검증
- JWT에서 현재 사용자 ID 추출
- Service 호출
- 응답 DTO 반환

Controller가 Repository를 직접 호출하거나 등록 규칙을 처리하지 않도록 분리했다.

등록 API는 다음과 같다.

```http
POST /api/products
Authorization: Bearer {access-token}
Content-Type: application/json
```

요청 예시:

```json
{
  "title": "업무용 노트북",
  "manufacturer": "Samsung",
  "modelName": "NT960X",
  "serialNumber": "AB-12 34",
  "purchaseDate": "2026-01-10",
  "warrantyExpiresAt": "2027-01-10",
  "laptop": {
    "cpuSpec": "Intel Core Ultra 7",
    "ramGb": 32,
    "storageGb": 1024,
    "storageType": "SSD",
    "gpuSpec": "Intel Arc",
    "displaySpec": "16인치 2880x1800",
    "batteryHealthPercent": 92,
    "osName": "Windows 11"
  }
}
```

성공 응답은 시리얼 관련 값을 포함하지 않는다.

```json
{
  "success": true,
  "data": {
    "id": 10,
    "category": "LAPTOP",
    "title": "업무용 노트북",
    "manufacturer": "SAMSUNG",
    "modelName": "NT960X",
    "purchaseDate": "2026-01-10",
    "warrantyExpiresAt": "2027-01-10"
  }
}
```

### 3.2 인증 사용자 처리

Spring Security가 검증한 JWT의 `sub` 값을 사용자 ID로 사용한다.

```text
JWT subject "42" → ownerId 42
```

JWT가 없거나 `sub`가 숫자가 아니거나 0 이하이면 `AUTH_001` 오류로 거부한다. 요청 본문에서 `ownerId`를 받지 않기 때문에 사용자가 다른 사람의 ID로 제품을 등록할 수 없다.

### 3.3 Service와 트랜잭션

`ProductRegistrationService.register()` 전체에 `@Transactional`을 적용했다.

처리 순서는 다음과 같다.

1. 제조사와 시리얼 정규화
2. 시리얼 HMAC 지문과 마지막 4자리 생성
3. 동일 지문 존재 여부 확인
4. `products` 저장
5. `laptop_details` 저장
6. 응답 모델 생성

제품 저장은 성공했지만 노트북 상세 저장이 실패하면 전체 트랜잭션이 롤백된다. 따라서 상세 정보 없는 불완전한 노트북 제품이 남지 않는다.

### 3.4 중복 등록과 동시성

중복 등록은 두 단계로 막는다.

1. Repository의 `existsBySerialNumberFingerprint()`로 빠르게 사전 검사한다.
2. DB UNIQUE 제약으로 동시에 들어온 요청까지 최종 차단한다.

사전 검사만으로는 다음 경쟁 조건을 막을 수 없다.

```text
요청 A: 중복 없음 확인
요청 B: 중복 없음 확인
요청 A: 저장 성공
요청 B: 저장 시도
```

따라서 `saveAndFlush()` 시 발생하는 유니크 제약 위반도 `PRODUCT_002`로 변환한다.

중복 응답:

```http
HTTP/1.1 409 Conflict
```

```json
{
  "code": "PRODUCT_002",
  "message": "이미 등록된 제품입니다. 현재 소유자에게 소유권 이전을 요청하세요."
}
```

## 4. DB 변경

기존 Migration은 수정하지 않고 새로운 `V5__protect_product_serial_number.sql`을 추가했다.

V5의 역할은 다음과 같다.

- `serial_last4` 컬럼 추가
- 기존 행에는 원문을 복원하지 않고 안전한 대체값 적용
- `serial_number_ciphertext` 컬럼 제거
- 마지막 4자리 길이 제약 추가
- 내 제품 목록을 위한 `(owner_id, created_at, id)` 인덱스 추가

암호화된 전체 시리얼도 복호화가 가능한 데이터이므로 WBS의 “원문 미저장” 취지에 맞지 않는다. 이에 따라 암호화 키와 복호화 로직도 제거했다.

## 5. 입력 검증

API 요청에서 다음 항목을 검증한다.

| 필드 | 조건 |
|---|---|
| `title` | 필수, 최대 200자 |
| `manufacturer` | 필수, 최대 100자 |
| `modelName` | 필수, 최대 100자 |
| `serialNumber` | 필수, 4~100자 |
| `ramGb` | 입력 시 1 이상 |
| `storageGb` | 입력 시 1 이상 |
| `batteryHealthPercent` | 입력 시 0~100 |
| `laptop` | 필수 |

미확인 노트북 사양은 ERD 정책에 따라 `null`로 저장할 수 있다.

## 6. 작성한 테스트

### 시리얼 보호 테스트

- 표기가 다른 동일 시리얼의 지문이 같은지 확인
- 다른 시리얼의 지문이 다른지 확인
- 제조사가 다르면 지문이 다른지 확인
- 잘못된 제조사와 시리얼 입력 거부
- 마지막 4자리 추출 확인

### 제품 등록 Service 테스트

- JWT 사용자 ID가 제품 소유자로 저장되는지 확인
- 제조사 정규화 확인
- 제품과 노트북 상세가 함께 저장되는지 확인
- 사전 중복 검사 시 저장하지 않는지 확인
- 동시 요청에 해당하는 DB 유니크 충돌을 409용 오류로 변환하는지 확인

### Controller 및 인증 테스트

- JWT `sub`가 등록 Command의 `ownerId`로 전달되는지 확인
- 응답 DTO에 시리얼이 포함되지 않는 구조인지 확인
- 숫자가 아닌 JWT `sub`를 거부하는지 확인

## 7. 검증 결과

작업 완료 시점 기준 검증 결과는 다음과 같다.

```text
spotlessCheck: 성공
test: 17개 성공, 실패 0개
build: 성공
Flyway V5 실제 PostgreSQL 적용: 성공
Hibernate ddl-auto=validate: 성공
Spring Boot 애플리케이션 기동: 성공
```

## 8. 현재 남은 작업

### 8.1 회원 테이블 외래키

ERD에는 `products.owner_id`가 `users.id`를 참조하도록 되어 있다. 현재 회원 도메인과 `users` 테이블이 아직 구현되지 않아 FK는 추가하지 않았다.

회원 테이블 구현 후 기존 Migration을 수정하지 말고 새 Migration으로 FK를 추가해야 한다.

### 8.2 HTTP·DB 통합 테스트

현재 Controller와 Service의 단위 테스트는 있지만 다음 전체 흐름을 검증하는 통합 테스트가 필요하다.

```text
서명된 JWT
  → POST /api/products
  → 요청 검증
  → products/laptop_details 저장
  → 실제 응답 확인
```

실제 PostgreSQL에서 동일 제품을 동시에 등록하는 테스트도 추가하면 중복 방지의 신뢰도가 높아진다.

### 8.3 구매 이력 자동 생성

ERD의 제품 등록 흐름은 구매일이 있을 때 `PURCHASE` 이력을 자동 생성하도록 정의한다. 현재 이력 도메인이 구현되지 않았으므로 이번 범위에는 포함하지 않았다.

이력 기능이 만들어지면 제품·노트북 상세·최초 구매 이력을 같은 트랜잭션에서 저장할지 확정해야 한다.

### 8.4 제조사별 시리얼 규칙

현재는 공통 정규화만 제공한다. 향후에는 제조사별 전략을 추가할 수 있다.

```text
SamsungSerialNormalizer
AppleSerialNormalizer
LgSerialNormalizer
```

다만 실제 제조사 규칙이 확정되기 전에는 과도한 추상화를 추가하지 않는다.

### 8.5 Git과 팀 공유

현재 제품 구현과 Migration은 로컬 `feature/model` 브랜치의 미커밋 변경사항이다. 다음 단계는 변경 파일 검토 후 커밋하고 PR을 통해 공유하는 것이다.

## 9. 이번 작업에서 익힐 핵심 개념

1. 민감한 식별자는 기능상 꼭 필요한 최소 데이터만 저장한다.
2. 시리얼처럼 원문 복원 없이 동일 여부만 확인할 값은 암호화보다 HMAC 기반 식별이 적합하다.
3. 애플리케이션 사전 검사만 믿지 말고 DB UNIQUE 제약을 최종 방어선으로 사용한다.
4. 연관된 여러 저장 작업은 하나의 트랜잭션으로 묶는다.
5. 소유자 ID는 요청 본문이 아니라 검증된 인증 정보에서 가져온다.
6. Entity를 API 응답으로 직접 반환하지 않고 별도 DTO를 사용한다.
7. 이미 적용될 수 있는 Flyway Migration은 수정하지 않고 새 버전으로 변경한다.
8. 단위 테스트 통과와 실제 DB Migration 성공은 서로 다른 검증 단계다.
