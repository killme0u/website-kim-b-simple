# API 설계 규약

> 상태: 현재 관행은 `확인됨`(코드에서 관찰), 규약 제안은 `제안`입니다.
> **현재 API가 규약을 어기는 지점도 함께 적습니다.** 새 API는 규약을 따르고,
> 기존 위반은 고칠 때 함께 정리합니다.

## G-001 경로 `확인됨` / `제안`

### 현재 관행

| 규칙 | 예 | 준수 |
|---|---|---|
| `/api` 접두사 | `/api/boards` | 전부 |
| 복수형 컬렉션 | `/api/posts`, `/api/comments` | 대체로 |
| 중첩은 1단계까지 | `/api/boards/{slug}/posts` | 준수 |
| 소문자 + 하이픈 | `/api/members/verify-email` | 준수 |

### 위반 지점 `확인됨`

**`/api/me` 접두사를 두 컨트롤러가 나눠 씁니다.**

| 경로 | 컨트롤러 |
|---|---|
| `GET /api/me` | `AuthController` |
| `GET /api/me/posts` | `MyPageController` |
| `GET /api/me/comments` | `MyPageController` |

같은 리소스 트리인데 소유자가 둘입니다. 새 `/api/me/*` 엔드포인트를 추가할 때
어디에 넣을지 매번 판단해야 합니다.

**동사가 경로에 있습니다.**

| 경로 | 성격 |
|---|---|
| `/api/members/verify-email` | 행위 |
| `/api/members/password-reset/request` | 행위 |
| `/api/posts/{id}/like` | 행위 |
| `/api/members/username-availability` | 조회 |

RESTful 순수주의로는 위반이지만, **이 경우는 받아들일 만합니다.**
"이메일 인증"이나 "좋아요 토글"을 리소스로 모델링하면 오히려 어색해집니다.
실용적 선택으로 유지합니다. `제안`

## G-002 HTTP 메서드 `제안`

### 규약

| 메서드 | 용도 | 멱등 | 상태 변경 |
|---|---|---|---|
| GET | 조회만 | 예 | **없어야 함** |
| POST | 생성, 행위 | 아니오 | 있음 |
| PUT | 전체 수정 | 예 | 있음 |
| DELETE | 삭제 | 예 | 있음 |

### 위반 1 — GET이 상태를 바꿈 `확인됨`

두 곳입니다.

| 경로 | 부수효과 |
|---|---|
| `GET /api/posts/{id}` | 조회수 증가 |
| `GET /api/members/verify-email` | 계정 활성화 + 토큰 소비 |

**조회수 증가**는 게시판에서 흔한 타협이라 그대로 두는 편이 실용적입니다.
다만 크롤러·프리페치·캐시가 조회수를 부풀립니다.

**이메일 인증**은 더 문제입니다. 메일 클라이언트나 보안 게이트웨이가
링크를 미리 열면 토큰이 소비되고, 사용자가 클릭했을 때는 이미 사용된 토큰이 됩니다.

`plan.md`의 API 계약표는 `POST /api/members/verify-email`로 적혀 있어
구현과 다릅니다. 어느 쪽이 정본인지 결정이 필요합니다. `미결정`

GET을 유지하려면 SPA가 토큰을 받아 POST를 다시 보내는 방식이 대안입니다.

### 위반 2 — 민감값이 쿼리 파라미터 `확인됨`

```
DELETE /api/posts/{id}?guestPassword=secret
```

`PUT`은 비밀번호를 본문에 담는데 `DELETE`만 쿼리입니다.

쿼리 파라미터는 **접근 로그, 프록시 로그, 브라우저 히스토리, Referer 헤더**에 남습니다.
비회원 글 비밀번호가 여러 곳에 평문으로 기록됩니다. `미결정`

`DELETE`에 본문을 넣는 것이 표준상 애매하다는 점이 원인으로 보입니다.
대안: `POST /api/posts/{id}/delete` 또는 헤더 사용.

## G-003 상태 코드 `제안`

### 규약

| 코드 | 사용 |
|---|---|
| 200 | 조회 성공, 본문 있는 성공 |
| 201 | 리소스 생성. 본문에 식별자 |
| 202 | 비동기 접수 (완료 보장 없음) |
| 204 | 본문 없는 성공 |
| 400 | 요청 형식·검증 오류 |
| 401 | 인증 필요 |
| 403 | 권한 없음 |
| **404** | **리소스 없음** |
| 409 | 상태 충돌 (중복 등) |
| 415 | 지원하지 않는 미디어 타입 |
| 500 | 서버 오류만 |

### 현재 준수 상황 `확인됨`

201·202·204 사용은 정확합니다.

| 코드 | 사용처 | 평가 |
|---|---|---|
| 201 | 가입, 글 작성, 댓글 작성 | 적절 |
| 202 | 메일 발송 요청 3종 | **적절** — 실제 발송이 비동기라 정확한 표현 |
| 204 | 로그아웃, 수정·삭제, 좋아요 | 적절 |

### 위반 3 — 404를 쓰지 않음 `확인됨`

**404가 한 번도 나오지 않습니다.**

```
GET /api/posts/999999     → 500  (NoSuchElementException 미처리)
GET /api/comments/999999  → 500
GET /api/files/없는이름     → 500  (RuntimeException)
GET /api/boards/없는slug/posts → 500
```

없는 게시판만 예외적으로 400을 냅니다(`BoardService.getBoardBySlug`).

같은 "없는 리소스"가 경로에 따라 400과 500으로 갈립니다.

영향:
- 클라이언트가 "없음"과 "장애"를 구분할 수 없음
- 모니터링에서 정상적인 404가 오류율을 부풀림
- Spring 기본 오류 응답에 예외 정보가 노출될 수 있음

**해결**: `GlobalExceptionHandler`에 핸들러 하나를 추가하면 됩니다. `제안`

```java
@ExceptionHandler(NoSuchElementException.class)
public ResponseEntity<ApiError> onNotFound(NoSuchElementException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
}
```

기존 동작을 바꾸지 않고 500만 404로 옮깁니다.

### 위반 4 — 409의 의미 과부하 `확인됨`

`DataIntegrityViolationException`을 전부 409 `DUPLICATE`로 바꿉니다.
그런데 UNIQUE 위반뿐 아니라 **CHECK 제약 위반도 여기로 옵니다.**

```
POST /api/boards/free/posts   (비로그인, guestNickname 없음)
  → post_author_ck 위반
  → 409 "이미 사용 중인 값이거나 중복된 데이터입니다."
```

"중복"이 아닌데 중복이라고 말합니다. 디버깅을 어렵게 만듭니다. `미결정`

## G-004 응답 형식 `확인됨`

### 성공 응답

| 형태 | 사용처 |
|---|---|
| 객체 | 단건 조회 (`MeResponse`, `PostResponse`) |
| 배열 | 게시판 목록, 댓글 목록 |
| `Page` | 게시글 목록, 마이페이지 |
| `{ "id": n }` | 생성 |
| `{ "status": "..." }` | 상태 변경 |

**생성 응답에 `Location` 헤더가 없습니다.** `{ "id": ... }`만 줍니다.
REST 관례로는 `Location: /api/posts/123`을 함께 주는 것이 맞습니다. `제안`

### 오류 응답

```json
{ "code": "BAD_REQUEST", "message": "..." }
```

일관됩니다. 다만 Spring Security 필터 층의 401·403은 **본문이 없습니다.**
같은 상태 코드가 두 형태로 나옵니다.

### `Page` 직렬화 `미결정`

Spring Data `Page`를 그대로 직렬화합니다. 이는 Spring 버전에 따라
구조가 바뀔 수 있는 내부 표현입니다. Spring Boot 3.3부터는
직렬화 방식 변경 경고가 있었습니다.

**전용 DTO로 감싸는 것이 안전합니다.** `제안`

## G-005 명명 `확인됨`

| 대상 | 규칙 | 예 |
|---|---|---|
| JSON 필드 | camelCase | `viewCount`, `requiresAuthToRead` |
| 경로 | 소문자 + 하이픈 | `verify-email` |
| 쿼리 파라미터 | camelCase | `guestPassword`, `boardSlug` |
| 오류 코드 | UPPER_SNAKE | `AUTH_REQUIRED` |

### 주의 — boolean 필드 직렬화 `확인됨`

`PostResponse`와 `CommentResponse`의 Java 필드명은 `isOwner`입니다.
Lombok이 `isOwner()` 게터를 만들고, Jackson은 boolean의 `isXxx()` 게터를
`xxx`로 직렬화합니다. 따라서 **JSON 키는 `owner`일 가능성이 높습니다.**

실제 응답으로 검증하지 못했습니다. 프론트엔드 코드와 대조해 확인이 필요합니다.
`openapi.yaml`에도 `[확인필요]`로 표시했습니다.

**규약**: boolean 필드에 `is` 접두사를 붙이지 마세요. `owner`, `active`처럼 씁니다. `제안`

## G-006 인증·CSRF `확인됨`

| 규칙 | 내용 |
|---|---|
| 인증 | 세션 쿠키. `Authorization` 헤더 미사용 |
| CSRF | 변경 요청은 `X-XSRF-TOKEN` 필수 |
| 면제 | 로그인, 회원가입만 |

**새 엔드포인트를 CSRF 면제 목록에 넣지 마세요.**
두 예외는 "세션이 없어 토큰을 받을 수 없다"는 구조적 이유가 있고,
둘 다 CAPTCHA나 자격 증명 검증이 대신 막습니다.

## G-007 권한 판정 위치 `확인됨` / `제안`

현재 2층 구조입니다.

```
층 1: SecurityConfig 경로 규칙   — 대부분 permitAll
층 2: 도메인 (Board, Post)        — 실제 판정
```

### 새 엔드포인트를 만들 때 `제안`

**반드시 다음 중 하나를 하세요.**

1. `SecurityConfig`에서 `authenticated()`로 막거나
2. 서비스에서 `board.checkReadable(actor)` / `checkWritable(actor)`를 호출하거나
3. 명시적으로 "누구나 접근 가능"임을 판단하고 주석으로 남기거나

셋 다 안 하면 무방비로 열립니다.

### 실제로 빠뜨린 예 `확인됨`

```java
.requestMatchers(HttpMethod.POST, "/api/boards/*/posts", "/api/files").permitAll()
```

`POST /api/files`가 `permitAll`인데 `FileController`와 `FileStorageService`에
어떤 권한 판정도 없습니다. **누구나 100MB까지 업로드할 수 있습니다.**

이것이 G-007이 필요한 이유입니다.

## G-008 페이징 `확인됨`

Spring `Pageable` 표준을 씁니다 — `page`, `size`, `sort`.

| 엔드포인트 | 페이징 |
|---|---|
| `GET /api/boards/{slug}/posts` | 예 |
| `GET /api/me/posts` | 예 |
| `GET /api/me/comments` | 예 |
| `GET /api/posts/{id}/comments` | **아니오** |
| `GET /api/boards` | 아니오 (게시판 3개) |

**댓글 목록에 페이징이 없습니다.** 댓글이 수천 개인 글이 생기면
한 번에 전부 내려갑니다. `미결정`

`size`에 상한이 없습니다. `?size=100000`을 막지 않습니다. `제안`

## G-009 버전 관리 `미결정`

**버전 전략이 없습니다.** `/api/v1` 같은 접두사도, 헤더 기반 협상도 없습니다.

SPA와 API가 한 아티팩트로 배포되므로(ADR-001) 현재는 문제가 아닙니다.
클라이언트와 서버 버전이 항상 일치합니다.

외부 클라이언트(모바일 앱 등)가 생기면 그때 필요합니다.

## 새 API 체크리스트 `제안`

- [ ] 경로가 `/api` 아래 복수형 컬렉션인가
- [ ] GET이 상태를 바꾸지 않는가
- [ ] 민감값이 쿼리 파라미터에 없는가
- [ ] 상태 코드가 G-003 표를 따르는가 (**없으면 404**)
- [ ] 권한 판정이 층 1 또는 층 2에 있는가 (G-007)
- [ ] 목록이면 페이징이 있는가
- [ ] boolean 필드에 `is` 접두사가 없는가
- [ ] `openapi.yaml`을 갱신했는가
- [ ] `features/api-behavior.md`를 갱신했는가

## 관련 문서

- [openapi.yaml](openapi.yaml) — 명세
- [../../features/api-behavior.md](../../features/api-behavior.md) — 실제 동작
- [../../features/error-policy.md](../../features/error-policy.md) — 오류 처리
- [integration-contracts.md](integration-contracts.md) — 외부 연동
