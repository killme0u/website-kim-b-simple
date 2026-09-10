# API 동작

> 상태: `확인됨` — 컨트롤러와 서비스 코드에서 도출한 실제 동작입니다.
> 스키마는 [../technology/api/openapi.yaml](../technology/api/openapi.yaml) 참조.

## 공통 규칙 `확인됨`

| 항목 | 값 |
|---|---|
| 기본 경로 | `/api` |
| 인증 방식 | 세션 쿠키 (`JSESSIONID`) |
| CSRF | 변경 요청에 `X-XSRF-TOKEN` 헤더 필요 |
| 동시 세션 | 계정당 1개 (`SecurityConfig.java:64`) |
| 세션 고정 방어 | 로그인 시 세션 ID 변경 |
| 오류 형식 | `{ "code": ..., "message": ... }` |

CSRF 예외: `POST /api/auth/login`, `POST /api/members/signup`

## 인증

### `POST /api/auth/login`

Spring Security `formLogin`이 처리합니다. `@RequestBody`가 아니라 **form 인코딩**입니다.

| 항목 | 값 |
|---|---|
| Content-Type | `application/x-www-form-urlencoded` |
| 파라미터 | `username`, `password` |
| 성공 | 200 (`JsonAuthenticationSuccessHandler`) |
| 실패 | `JsonAuthenticationFailureHandler` |
| CSRF | 면제 |

계정 상태 검사가 여기서 일어납니다.

| 상태 | 결과 |
|---|---|
| `PENDING`, `ACTIVE` | 로그인 성공 |
| `SUSPENDED` | 실패 (`isAccountNonLocked`) |
| `DELETED` | 실패 (`isEnabled`) |

### `POST /api/auth/logout`

| 항목 | 값 |
|---|---|
| 성공 | 204 No Content |
| CSRF | **필요** |

CSRF 토큰 없이 호출하면 403입니다. 회귀 테스트 `SecurityConfigTest`가 이를 고정합니다.

### `GET /api/me`

| 항목 | 값 |
|---|---|
| 인증 | 필요 |
| 성공 | 200 + `MeResponse` |
| 비로그인 | 401 `AUTH_REQUIRED` |

응답 필드: `id`, `username`, `name`, `nickname`, `email`, `status`, `role`, `mustChangePassword`
(`member/adapter/in/web/dto/MeResponse.java`)

`nickname`이 응답에 포함됩니다(2026-09-10). 선택 항목이며 null일 수 있습니다.

## 회원

### `POST /api/members/signup`

| 항목 | 값 |
|---|---|
| 성공 | **201** + `{ "id": 123 }` |
| CSRF | 면제 |
| 인증 | 불필요 |

요청 검증 (`SignupCommand`)

| 필드 | 제약 | 필수 |
|---|---|---|
| `username` | 4~30자 | 예 |
| `password` | 8~100자 | 예 |
| `name` | ~50자 | 예 |
| `nickname` | ~30자 | 아니오 |
| `email` | 이메일 형식 | 예 |
| `phone` | ~20자 | **예** |
| `captchaToken` | ~4096자 | 예 |
| `termsAccepted` | true만 허용 (`@AssertTrue`) | 예 |

처리 순서 (`member/application/SignupService.java:28-47`)

1. 약관 동의 검증 → false 또는 null이면 400, **회원 생성 안 됨**
2. CAPTCHA 검증 → 실패 시 400, **회원 생성 안 됨**
3. 닉네임 정규화 후 `PENDING` 회원 저장
4. 24시간 인증 토큰 발급 (해시만 저장)
5. `SignupCompleted` 이벤트 발행
6. 커밋 후 비동기로 메일 발송

**메일 발송 실패는 응답에 반영되지 않습니다.** 201을 받아도 메일이 갔다는 보장이 없습니다.

### `GET /api/members/verify-email?token=`

| 항목 | 값 |
|---|---|
| 성공 | 200 + `{ "status": "verified" }` |
| 실패 | 400 `BAD_REQUEST` |
| 인증 | 불필요 |

**GET인데 상태를 바꿉니다.** 메일 클라이언트의 링크 프리페치가 토큰을 소비할 수 있습니다.
`plan.md`의 API 계약표에는 `POST /api/members/verify-email`로 적혀 있으나
구현은 GET입니다. 문서와 구현 불일치. `미결정`

### `POST /api/members/verify-email/resend`

| 항목 | 값 |
|---|---|
| 성공 | **202** + `{ "status": "sent" }` |
| 인증 | 불필요 |

계정이 없거나 이미 `ACTIVE`여도 **202를 반환**합니다 (계정 열거 방지).
`PENDING`인 경우에만 실제로 메일을 보냅니다.

**재발송 횟수 제한이 없습니다.** 같은 이메일로 무한히 호출할 수 있고,
호출할 때마다 `verification_token` 행이 하나씩 쌓입니다. `미결정`
(이전 토큰을 무효화하지 않으므로 유효한 토큰이 여러 개 공존합니다.)

### `POST /api/members/password-reset/request`

| 항목 | 값 |
|---|---|
| 성공 | **202** + `{ "status": "requested" }` |
| CAPTCHA | **필요** |
| 인증 | 불필요 |

CAPTCHA 실패 시에만 400이 납니다. 계정 유무는 응답에 드러나지 않습니다.
토큰 유효 기간은 1시간입니다.

### `POST /api/members/password-reset/change`

| 항목 | 값 |
|---|---|
| 성공 | 200 + `{ "status": "changed" }` |
| 실패 | 400 (토큰 무효·만료·사용됨) |
| 인증 | 불필요 (토큰이 곧 인증) |

성공 시 `mustChangePassword = false`, `tempPasswordExpiresAt = null`로 초기화됩니다.

**기존 세션을 무효화하지 않습니다.** 비밀번호를 바꿔도 다른 곳에 남은 로그인이 유지됩니다. `미결정`

### `GET /api/members/username-availability?username=`

| 항목 | 값 |
|---|---|
| 응답 | 200 + `{ "available": true }` |
| 인증 | 불필요 |

**정규화 없이 원본으로 조회**합니다(`MemberController.java:74-78`).
저장도 `trim`하지 않으므로 현재는 일치하지만, 앞뒤 공백이 든 아이디가 만들어질 수 있습니다.
`todo.md`의 후속 검토 항목입니다. `미결정`

### `GET /api/members/nickname-availability?nickname=`

| 항목 | 값 |
|---|---|
| 응답 | 200 + `{ "available": true }` |
| 인증 | 불필요 |

`Member.normalizeNickname`을 거쳐 조회합니다 — 저장 경로와 같은 규칙(결정 D5).

| 입력 | 정규화 | 결과 |
|---|---|---|
| `"단팥빵"` (이미 사용 중) | `"단팥빵"` | `false` |
| `" 단팥빵"` | `"단팥빵"` | `false` |
| `""` 또는 `"   "` | `null` | `true` (닉네임 미사용) |

회귀 테스트: `MemberControllerTest`, `MemberTest`

### `POST /api/members/username-recovery`

| 항목 | 값 |
|---|---|
| 성공 | **202** + `{ "message": "가입 이메일로 아이디 안내를 전송했습니다." }` |
| CAPTCHA | **없음** |
| 인증 | 불필요 |

가입·비밀번호 재설정에는 CAPTCHA가 있는데 여기만 없습니다. `미결정`

## 게시판

### `GET /api/boards`

권한 검사가 없습니다. 누구나 전체 목록을 봅니다. `displayOrder` 오름차순.

### `GET /api/boards/{slug}`

| 상황 | 응답 |
|---|---|
| 정상 | 200 + `BoardResponse` |
| 회원제 게시판 + 비로그인 | 401 `AUTH_REQUIRED` |
| 없는 slug | 400 `BAD_REQUEST` |

## 게시글

### `GET /api/boards/{boardSlug}/posts`

| 파라미터 | 설명 |
|---|---|
| `keyword` | 선택. 제목·본문 `LIKE` 검색 |
| `page`, `size`, `sort` | Spring `Pageable` 표준 |

응답은 Spring `Page` 직렬화 형태입니다 (`content`, `totalElements`, `totalPages` 등).

| 상황 | 응답 |
|---|---|
| 회원제 게시판 + 비로그인 | 401 |
| 없는 slug | **500** (`NoSuchElementException`) `미결정` |

같은 "없는 게시판"인데 `GET /api/boards/{slug}`는 400, 여기는 500입니다.

### `POST /api/boards/{boardSlug}/posts`

| 항목 | 값 |
|---|---|
| 성공 | **201** + `{ "id": 123 }` |
| HTTP 층 | `permitAll` — 판정은 도메인 |

요청 (`PostCommand`)

| 필드 | 제약 |
|---|---|
| `title` | `@NotBlank` |
| `content` | `@NotBlank` |
| `guestNickname` | 검증 없음 |
| `guestPassword` | 검증 없음 |
| `attachments` | `FileResponse` 배열 |

**회원/비회원 분기는 세션으로 결정됩니다.** 로그인 상태면 `guestNickname`을 보내도 무시하고
회원 글로 저장합니다(`PostService.java:80-81`).

주의할 점 `미결정`:
- `guestPassword`에 길이 제약이 없습니다. 비회원이 1자 비밀번호를 쓸 수 있습니다.
- 비로그인 + `guestNickname`이 `null`이면 DB `post_author_ck` 위반으로 409 `DUPLICATE`가 납니다. 오해를 부르는 코드입니다.
- `allowsAttachment`를 검사하지 않아 첨부 불가 게시판에도 첨부가 붙습니다.

### `GET /api/posts/{id}`

| 항목 | 값 |
|---|---|
| 성공 | 200 + `PostResponse` (첨부 목록 포함) |
| 없음·삭제됨 | **500** `미결정` |

**부수효과: 조회수가 증가합니다.**

| 주체 | 동작 |
|---|---|
| 로그인 | `post_view_log`에 오늘 기록이 없을 때만 +1 |
| 비로그인 | 항상 +1 |

응답에는 권한 판단용 플래그가 포함됩니다 — `PostResponse.from(post, memberId, isAdmin, attachments)`.

### `PUT /api/posts/{id}`

| 항목 | 값 |
|---|---|
| 성공 | **204** No Content |
| 권한 없음 | 403 `FORBIDDEN` |
| HTTP 층 | `permitAll` |

`guestPassword`를 본문에 담습니다. 판정은 `Post.checkEditable`.

### `DELETE /api/posts/{id}`

| 항목 | 값 |
|---|---|
| 성공 | **204** |
| `guestPassword` | **쿼리 파라미터** (`?guestPassword=`) |

수정은 본문, 삭제는 쿼리 파라미터로 비밀번호를 받는 비대칭입니다.
**쿼리 파라미터는 접근 로그·프록시 로그·브라우저 히스토리에 남습니다.** `미결정`
→ [../security/threat-model.md](../security/threat-model.md) T-004

### `POST /api/posts/{id}/like`

| 항목 | 값 |
|---|---|
| 성공 | **204** |
| 비로그인 | 401 `AUTH_REQUIRED` |
| 동작 | 토글 |

응답 본문이 없어 클라이언트는 **좋아요가 켜졌는지 꺼졌는지 알 수 없습니다.**
프론트엔드가 낙관적 업데이트로 자체 추정합니다. `미결정`

## 댓글

### `GET /api/posts/{postId}/comments`

페이징하지 않고 전체를 반환합니다. `id` 오름차순. 삭제된 것 제외.
게시판 읽기 권한 검사를 거칩니다.

### `POST /api/posts/{postId}/comments`

| 상황 | 응답 |
|---|---|
| 성공 | 201 + `{ "id": ... }` |
| 비로그인 | 401 `AUTH_REQUIRED` |
| 댓글 불가 게시판 | 400 `BAD_REQUEST` (영어 메시지) |

### `PUT /api/comments/{id}` · `DELETE /api/comments/{id}`

작성자 본인 또는 관리자만 가능. 성공 시 204. 그 외 403.

## 마이페이지

### `GET /api/me/posts` · `GET /api/me/comments`

| 항목 | 값 |
|---|---|
| 인증 | 필요 (401) |
| 페이징 | `Pageable` |
| 정렬 | `id` 내림차순 고정 |
| 필터 | 삭제되지 않은 것 |

`/api/me/posts`는 **회원 글만** 반환합니다. 로그인 전에 비회원으로 쓴 글은 보이지 않습니다
(`member_id`로 조회하므로).

## 파일

### `POST /api/files`

| 항목 | 값 |
|---|---|
| Content-Type | `multipart/form-data` |
| 파라미터 | `file` |
| 성공 | 200 + `FileResponse` |
| 크기 제한 | 100MB |
| HTTP 층 | `permitAll` — **비로그인도 업로드 가능** |

응답의 `contentType`과 `mediaKind`는 **클라이언트가 보낸 `Content-Type` 기반**입니다.
서버가 내용을 검사하지 않습니다. `미결정`

게시글과 연결되지 않은 업로드는 디스크에 그대로 남습니다(고아 파일).

### `GET /api/files/{storedName}`

| 파라미터 | 효과 |
|---|---|
| 없음 | `Content-Disposition: inline` |
| `?download=아무값` | `Content-Disposition: attachment` |

`Content-Type`은 **서버가 `Files.probeContentType`으로 재판정**합니다
(`FileController.java:55-61`). 업로드 시 저장된 값을 쓰지 않습니다.

| 상황 | 응답 |
|---|---|
| 정상 | 200 |
| 경로 탈출 시도 | **500** (`SecurityException`) `미결정` |
| 파일 없음 | **500** (`RuntimeException`) `미결정` |

**인증이 없습니다.** `storedName`(UUID)만 알면 누구나 받을 수 있습니다.
회원제 게시판의 첨부도 마찬가지입니다.
→ [../security/threat-model.md](../security/threat-model.md) T-003

## HTTP 상태 코드 요약 `확인됨`

| 코드 | 사용처 |
|---|---|
| 200 | 조회, 인증 완료, 비밀번호 변경, 파일 |
| 201 | 회원가입, 글 작성, 댓글 작성 |
| 202 | 메일 발송 요청 3종 (재발송·비밀번호재설정·아이디찾기) |
| 204 | 로그아웃, 글 수정·삭제, 댓글 수정·삭제, 좋아요 |
| 400 | 검증 실패, CAPTCHA 실패, 토큰 무효, 없는 게시판(일부) |
| 401 | 인증 필요 |
| 403 | 권한 없음, CSRF 실패 |
| 409 | UNIQUE 제약 위반 |
| 415 | 지원하지 않는 파일 형식 |
| 500 | **없는 리소스 조회** (404여야 함), 파일 오류 |

## 관련 문서

- [../technology/api/openapi.yaml](../technology/api/openapi.yaml) — 기계 판독 명세
- [error-policy.md](error-policy.md) — 오류 상세
- [../technology/api/api-guidelines.md](../technology/api/api-guidelines.md) — 설계 규약과 위반 사례
