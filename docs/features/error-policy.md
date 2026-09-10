# 오류 정책

> 상태: `확인됨` — `common/GlobalExceptionHandler.java`와 `config/SecurityConfig.java`에서 도출.

## 응답 형식 `확인됨`

오류 본문은 항상 두 필드입니다 (`common/ApiError.java`).

```json
{ "code": "DUPLICATE", "message": "이미 사용 중인 값이거나 중복된 데이터입니다." }
```

| 필드 | 용도 |
|---|---|
| `code` | 클라이언트 분기용 안정 식별자 |
| `message` | 사용자에게 보여줄 한국어 문구 |

프론트엔드는 `errorMessage(error, fallback)`으로 `message`를 꺼냅니다
(`frontend-react/src/lib/axios.ts:18-23`). `code`로 분기하는 곳은 현재 없습니다.

## 오류 코드 전체 `확인됨`

| code | HTTP | 발생 조건 | 핸들러 |
|---|---|---|---|
| `DUPLICATE` | 409 | DB UNIQUE 제약 위반 | `GlobalExceptionHandler:16-20` |
| `AUTH_REQUIRED` | 401 | 로그인이 필요한 동작을 비로그인으로 시도 | `:22-26` |
| `FORBIDDEN` | 403 | 권한 없음 또는 비회원 글 비밀번호 불일치 | `:28-34` |
| `UNSUPPORTED_FILE` | 415 | 허용되지 않는 파일 형식 | `:36-40` |
| `BAD_REQUEST` | 400 | `@Valid` 검증 실패 | `:42-50` |
| `BAD_REQUEST` | 400 | `IllegalArgumentException` | `:52-56` |

**같은 `BAD_REQUEST` 코드가 두 경로에서 나옵니다.** 검증 실패와 비즈니스 규칙 위반을
클라이언트가 구분할 수 없습니다.

## 예외 → 응답 매핑 `확인됨`

| 예외 | 응답 | 비고 |
|---|---|---|
| `DataIntegrityViolationException` | 409 `DUPLICATE` | 어떤 컬럼이 중복인지 알려주지 않음 |
| `AuthenticationRequiredException` | 401 `AUTH_REQUIRED` | 커스텀 예외 |
| `AccessDeniedException` | 403 `FORBIDDEN` | Spring Security 예외 재사용 |
| `UnsupportedFileTypeException` | 415 `UNSUPPORTED_FILE` | 커스텀 예외 |
| `MethodArgumentNotValidException` | 400 `BAD_REQUEST` | **첫 번째 필드 오류만** 반환 |
| `IllegalArgumentException` | 400 `BAD_REQUEST` | 예외 메시지를 그대로 노출 |

### `DUPLICATE`가 모호한 이유 `미결정`

`member` 테이블에는 UNIQUE 제약이 넷 있습니다: `username`, `email`, `nickname`(부분), 그리고
`verification_token.token_hash`. 어느 것이 충돌해도 같은 409 `DUPLICATE`가 나옵니다.

가입 화면에서 아이디가 중복인지 이메일이 중복인지 사용자가 알 수 없습니다.
중복 확인 API가 이 문제를 UX 층에서 가리고 있지만, 확인과 제출 사이의 경합에서는 드러납니다.

### 검증 실패가 하나만 나오는 이유 `확인됨`

```java
.map(f -> f.getField() + ": " + f.getDefaultMessage())
.findFirst().orElse("잘못된 요청입니다.")
```

(`GlobalExceptionHandler.java:45-47`)

`findFirst()`이므로 세 필드가 동시에 잘못돼도 하나만 알려줍니다.
사용자가 고치면 다음 오류가 나오는 반복이 생깁니다. `미결정`

메시지 형식은 `필드명: 기본메시지`이며 **영문 필드명이 그대로 노출**됩니다
(예: `password: size must be between 8 and 100`).

### `IllegalArgumentException` 메시지 노출 `확인됨`

예외 메시지를 그대로 응답에 넣습니다(`GlobalExceptionHandler.java:55`).
현재 이 경로로 나가는 메시지는 모두 의도된 한국어 문구입니다.

| 발생 위치 | 메시지 |
|---|---|
| `SignupService.java:31` | CAPTCHA 검증에 실패했습니다. |
| `VerificationService.java:37` | 유효하지 않은 이메일 인증 토큰입니다. |
| `VerificationService.java:39` | 만료되었거나 이미 사용된 이메일 인증 토큰입니다. |
| `VerificationService.java:67` | 유효하지 않은 비밀번호 재설정 토큰입니다. |
| `VerificationService.java:69` | 만료되었거나 이미 사용된 비밀번호 재설정 토큰입니다. |
| `BoardService.java:36` | 존재하지 않는 게시판입니다: {slug} |
| `CommentService.java:50` | Board doesn't allow comments |
| `VerificationToken.java:62` | 토큰이 이미 사용되었습니다. |

`CommentService`의 메시지만 영어입니다. 사용자에게 그대로 노출됩니다. `미결정`

위험: 앞으로 내부 정보를 담은 `IllegalArgumentException`이 추가되면 그대로 새어 나갑니다.

## Spring Security 층의 오류 `확인됨`

`GlobalExceptionHandler`를 거치지 않고 **본문 없이** 상태 코드만 반환합니다
(`config/SecurityConfig.java:57-60`).

| 상황 | 응답 | 본문 |
|---|---|---|
| 인증 필요 (`/api/**`) | 401 | 없음 |
| 접근 거부 | 403 | 없음 |
| CSRF 토큰 누락·불일치 | 403 | 없음 |
| 로그인 실패 | `JsonAuthenticationFailureHandler`가 처리 | — |

**같은 401이 두 형태로 나옵니다.**

- 필터 층: 본문 없음
- 도메인 층(`AuthenticationRequiredException`): `{"code":"AUTH_REQUIRED", ...}`

클라이언트는 두 경우를 모두 처리해야 합니다. `lib/axios.ts:14-16`의
`isUnauthorized`가 상태 코드만 보므로 현재는 문제가 되지 않습니다.

## 미처리 예외 `미결정`

핸들러가 없어 **500 + Spring 기본 오류 본문**으로 나가는 경로들입니다.

| 예외 | 발생 조건 | 위치 |
|---|---|---|
| `NoSuchElementException` | 없거나 삭제된 게시글·댓글 조회 | `orElseThrow()` 다수 |
| `NoSuchElementException` | 없는 게시판 slug로 글 목록 조회 | `PostService.java:38, 76` |
| `RuntimeException` | 파일을 찾을 수 없음 | `FileStorageService.java:78` |
| `RuntimeException` | 파일 저장 실패 | `FileStorageService.java:55` |
| `SecurityException` | 경로 탈출 시도 | `FileStorageService.java:72` |

### 가장 큰 문제: 존재하지 않는 리소스가 500 `확인됨`

```
GET /api/posts/999999   → 500  (NoSuchElementException)
```

**404여야 할 것이 500입니다.** 삭제된 글, 없는 글, 없는 댓글 모두 마찬가지입니다.

`PostService.getPost`의 `.orElseThrow()`는 인자가 없어 `NoSuchElementException`을 던지고
(`post/application/PostService.java:46`), 이 예외에 대한 핸들러가 없습니다.

영향:
- 클라이언트가 "없음"과 "서버 장애"를 구분할 수 없음
- 모니터링에서 정상적인 404가 오류율을 부풀림
- Spring 기본 오류 응답에 예외 타입이 노출될 수 있음

### `BoardService`와의 비대칭 `확인됨`

같은 "없는 리소스"인데 게시판만 다르게 처리됩니다.

| 대상 | 예외 | 응답 |
|---|---|---|
| 없는 게시판 (`getBoardBySlug`) | `IllegalArgumentException` | 400 `BAD_REQUEST` |
| 없는 게시판 (`listPosts`) | `NoSuchElementException` | 500 |
| 없는 게시글 | `NoSuchElementException` | 500 |

`BoardService.getBoardBySlug`만 명시적 예외를 던집니다(`board/application/BoardService.java:35-36`).
같은 게시판이라도 경로에 따라 400과 500으로 갈립니다.

### 파일 경로 탈출이 500 `확인됨`

`SecurityException`에 핸들러가 없어 500이 됩니다.
공격 시도가 정상적인 오류율에 섞여 들어가고, 로그에서 구분하기 어렵습니다.
403이 더 적절합니다. → [../security/threat-model.md](../security/threat-model.md) T-005

## 개선 제안 `제안`

우선순위 순입니다.

1. **`NoSuchElementException` → 404 핸들러 추가** — 한 줄로 가장 큰 문제가 해결됩니다
2. **`SecurityException` → 403 핸들러** — 공격 시도를 오류율에서 분리
3. **`RuntimeException` → 500 + 일반화된 메시지** — 내부 정보 노출 차단
4. **검증 실패 시 전체 필드 목록 반환** — `findFirst()` 제거
5. **`DUPLICATE`를 필드별로 세분화** — `DUPLICATE_USERNAME` 등
6. **`CommentService`의 영어 메시지를 한국어로**

1~3은 `GlobalExceptionHandler`에 메서드를 추가하는 것만으로 끝나며
기존 동작을 바꾸지 않습니다.

## 로그 정책 `확인됨`

| 대상 | 처리 |
|---|---|
| 메일 발송 실패 | `log.error` (`SignupMailListener.java:80`) — 사용자에게 알리지 않음 |
| SMTP 미설정 폴백 | `log.warn` (`MailConfig.java:40-43`) |
| SMTP 사용 | `log.info` (`MailConfig.java:46`) |
| CAPTCHA 실패 | **로그 없음** — 조용히 `false` 반환 |
| SQL | `show-sql: true` (`application.yml`) — 운영에서는 꺼야 함 |

CAPTCHA 실패가 전혀 기록되지 않아 스팸 유입 규모를 알 수 없습니다.
`ConfiguredCaptchaVerifier`의 모든 예외 경로가 로그 없이 `false`를 반환합니다
(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:60-67`). `미결정`

## 관련 문서

- [api-behavior.md](api-behavior.md) — 엔드포인트별 응답
- [../technology/api/api-guidelines.md](../technology/api/api-guidelines.md) — 설계 규약
- [../security/secure-coding-standard.md](../security/secure-coding-standard.md) — 오류 메시지와 정보 노출
