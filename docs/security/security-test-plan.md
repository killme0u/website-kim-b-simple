# 보안 테스트 계획

> 상태: 현재 자동 검증은 `확인됨`(테스트 파일 존재), 계획은 `제안`입니다.

## 현재 보안 관련 테스트 `확인됨`

전체 테스트 17개 중 **보안과 직접 관련된 것은 4개**입니다.

| 테스트 | 검증 내용 |
|---|---|
| `SecurityConfigTest.writesCsrfCookieOnEveryResponse` | 모든 응답에 `XSRF-TOKEN` 쿠키 |
| `SecurityConfigTest.logoutWithoutCsrfTokenIsForbidden` | CSRF 토큰 없는 로그아웃은 403 |
| `SecurityConfigTest.logoutWithCookieCsrfTokenSucceeds` | 원본 토큰을 헤더로 보내면 성공 |
| `SecurityConfigTest.spaShellIsReachableAnonymously` | SPA 셸이 비로그인에 열림 |

CSRF 하나만 덮여 있습니다.

## 검증되지 않는 보안 동작 `확인됨`

| 영역 | 테스트 | 관련 요구사항 |
|---|---|---|
| 게시판 읽기·쓰기 권한 | 없음 | SR-003-1 |
| 글 수정·삭제 권한 | 없음 | SR-003-2, SR-003-4 |
| 댓글 권한 | 없음 | SR-003-3 |
| CAPTCHA 실패 시 회원 미생성 | 없음 | T-007 |
| 토큰 만료·재사용 거부 | 없음 | PR-002-3 |
| 계정 상태별 로그인 차단 | 없음 | SR-001-4 |
| 계정 열거 방지 | 없음 | SR-008 |
| 경로 탈출 차단 | 없음 | SR-004-3 |
| SVG 강등 | 없음 | SR-005-1 |
| 중복 가입 거부 | 없음 | SC-010 |

## 1단계 — 도메인 단위 테스트 `제안`

**가장 저렴하고 가장 위험한 영역을 덮습니다.**
권한 판정이 도메인에 있어서 외부 의존 없이 검증됩니다.

### ST-001 게시글 수정·삭제 권한 — 최우선

`Post.checkEditable`의 9가지 경우입니다.
[access-control.md](access-control.md)의 표와 대응합니다.

| # | 요청자 | 글 종류 | 비밀번호 | 기대 |
|---|---|---|---|---|
| 1 | 관리자 | 타인의 회원 글 | — | 통과 |
| 2 | 관리자 | 비회원 글 | — | 통과 |
| 3 | 회원 | 본인 글 | — | 통과 |
| 4 | 회원 | 타인 글 | — | `AccessDeniedException` |
| 5 | 회원 | 비회원 글 | 정답 | **`AccessDeniedException`** |
| 6 | 익명 | 회원 글 | — | `AccessDeniedException` |
| 7 | 익명 | 비회원 글 | 정답 | 통과 |
| 8 | 익명 | 비회원 글 | 오답 | `AccessDeniedException` |
| 9 | 익명 | 비회원 글 | `null` | `AccessDeniedException` |

**5번이 가장 중요합니다.** 로그인한 사용자가 비밀번호를 추측해
남의 비회원 글을 고치는 것을 막는 동작입니다.
분기 순서가 바뀌면 이 방어가 사라지는데, 지금은 아무도 감시하지 않습니다.

필요한 것: `Optional<Member>`, `PasswordEncoder`. Spring 컨텍스트 불필요.

### ST-002 게시판 권한

`Board.checkReadable` / `checkWritable`

| 조건 | 기대 |
|---|---|
| `requiresAuthToRead=true` + 익명 | `AuthenticationRequiredException` |
| `requiresAuthToRead=true` + 회원 | 통과 |
| `requiresAuthToRead=false` + 익명 | 통과 |
| `requiresAuthToWrite=true` + 익명 | `AuthenticationRequiredException` |

`V4`가 `qna`·`archive`를 회원제로 바꾼 결정이 회귀하지 않게 고정합니다.

### ST-003 토큰 수명

`VerificationToken.isUsable`의 세 조건입니다.

| 조건 | 기대 |
|---|---|
| purpose 불일치 | `false` |
| `usedAt`이 설정됨 | `false` |
| `expiresAt` 경과 | `false` |
| 셋 다 정상 | `true` |

**현재는 시간 의존 테스트가 어렵습니다** — `ZonedDateTime.now()`를 직접 호출합니다.
`Clock` 주입으로 바꾸면 테스트가 쉬워집니다. `제안`

### ST-004 계정 상태별 로그인

`CustomUserDetails`의 두 훅입니다.

| 상태 | `isAccountNonLocked` | `isEnabled` |
|---|---|---|
| `PENDING` | `true` | **`true`** |
| `ACTIVE` | `true` | `true` |
| `SUSPENDED` | **`false`** | `true` |
| `DELETED` | `true` | **`false`** |

`PENDING`이 `true`인 것이 의도임을 테스트로 고정하면,
나중에 누군가 "버그 같다"며 고치는 것을 막을 수 있습니다.

### ST-005 미디어 종류 판정

`MediaKind.from`

| 입력 | 기대 |
|---|---|
| `image/svg+xml` | **`FILE`** (XSS 방어) |
| `image/png` | `IMAGE` |
| `video/mp4` | `VIDEO` |
| `audio/mpeg` | `AUDIO` |
| `text/html` | `FILE` |
| `application/pdf` | `FILE` |

SVG 케이스가 핵심입니다. `PRD.md` 2.5의 결정을 고정합니다.

## 2단계 — 슬라이스 테스트 `제안`

### ST-006 오류 → HTTP 매핑

`GlobalExceptionHandler`의 매핑입니다.

| 예외 | 기대 |
|---|---|
| `AuthenticationRequiredException` | 401 `AUTH_REQUIRED` |
| `AccessDeniedException` | 403 `FORBIDDEN` |
| `DataIntegrityViolationException` | 409 `DUPLICATE` |
| `UnsupportedFileTypeException` | 415 `UNSUPPORTED_FILE` |
| `MethodArgumentNotValidException` | 400 `BAD_REQUEST` |
| **`NoSuchElementException`** | **404** ← 현재는 500 |
| **`SecurityException`** | **403** ← 현재는 500 |

뒤의 두 줄은 아직 구현되지 않았습니다.
**고칠 때 테스트를 함께 쓰세요.**

### ST-007 계정 열거 방지

계정 유무와 무관하게 같은 응답이 나오는지 확인합니다.

| API | 존재하는 이메일 | 없는 이메일 | 기대 |
|---|---|---|---|
| `POST /api/members/password-reset/request` | 202 | 202 | **동일** |
| `POST /api/members/verify-email/resend` | 202 | 202 | 동일 |
| `POST /api/members/username-recovery` | 202 | 202 | 동일 |

응답 본문과 상태 코드가 모두 같아야 합니다.
응답 시간 차이는 이 수준에서 검증하지 않습니다.

### ST-008 CAPTCHA 실패 시 회원 미생성

| 조건 | 기대 |
|---|---|
| `captchaToken`이 잘못됨 | 400, `member` 행 생성 안 됨 |
| `captchaToken`이 `null` | 400 (`@NotBlank`) |
| CAPTCHA provider 오류 | 400, 회원 생성 안 됨 |

`CaptchaVerifier`를 대역으로 교체해 검증합니다.
`SignupMailListenerTest`의 `RecordingMailSender` 패턴을 따르면 됩니다.

`plan.md` 검증 기준의 첫 항목인데 아직 테스트가 없습니다.

## 3단계 — DB 통합 테스트 `제안`

Testcontainers가 필요합니다(PostgreSQL 전용 기능 의존).

### ST-009 중복 가입 거부

| 조건 | 기대 |
|---|---|
| 같은 `username` | 409 `DUPLICATE` |
| 같은 `email` | 409 `DUPLICATE` |
| 같은 `nickname` | 409 `DUPLICATE` |
| 둘 다 `nickname = null` | **성공** (부분 UNIQUE) |

마지막이 중요합니다 — 부분 UNIQUE 인덱스가 `NULL`을 중복으로 보지 않는 동작입니다.

### ST-010 CHECK 제약

| 조건 | 기대 |
|---|---|
| `member_id`와 `guest_nickname` 둘 다 있음 | CHECK 위반 |
| 둘 다 없음 | CHECK 위반 |
| 회원 글 (member만) | 성공 |
| 비회원 글 (guest만) | 성공 |

### ST-011 조회수·좋아요 중복 방지

| 조건 | 기대 |
|---|---|
| 같은 회원 같은 날 2회 조회 | `view_count` +1만 |
| 날짜가 바뀐 뒤 조회 | 추가 +1 |
| 같은 회원 좋아요 2회 | 토글되어 원복 |
| `like_count`가 0일 때 취소 | 음수가 되지 않음 |

## 4단계 — 수동 보안 점검 `제안`

자동화하기 어렵거나 인프라 수준인 항목입니다.

### 배포 전 필수 확인

| # | 확인 | 방법 |
|---|---|---|
| 1 | HTTPS 강제 | `curl -I http://<도메인>` → 리다이렉트 확인 |
| 2 | 쿠키 `Secure`·`SameSite` | 응답 `Set-Cookie` 헤더 확인 |
| 3 | `CAPTCHA_MODE != fake` | `printenv CAPTCHA_MODE` |
| 4 | `MAIL_DEBUG=false` | `printenv MAIL_DEBUG` |
| 5 | DB 비밀번호가 기본값 아님 | 설정 확인 |
| 6 | 5432가 외부 미노출 | `nmap` 또는 방화벽 확인 |
| 7 | `.env` 미커밋 | `git check-ignore -v .env` |
| 8 | 메일 발송기 확인 | 로그에서 `SMTP 메일 발송을 사용합니다` |

8번은 보안 항목입니다 — 폴백 상태면 **인증 토큰이 로그에 남습니다.**

### 침투 시나리오 점검 `제안`

[threat-model.md](threat-model.md)의 위협을 직접 시도해 봅니다.

| 시나리오 | 확인 |
|---|---|
| 인증 없이 파일 업로드 | `curl -F file=@x.bin <host>/api/files` → **현재 성공** |
| 회원제 게시판 첨부 무단 다운로드 | UUID로 직접 요청 → **현재 성공** |
| 경로 탈출 | `/api/files/..%2F..%2Fetc%2Fpasswd` → 차단 (500) |
| CSRF 없이 로그아웃 | 토큰 없이 POST → 403 |
| `fake` CAPTCHA로 가입 | 토큰 `dev-captcha` → 모드에 따라 |
| 로그인 무차별 대입 | 반복 요청 → **제한 없음** |
| 다른 사용자 글 수정 | 세션 A로 B의 글 PUT → 403 |

앞의 둘과 여섯 번째는 **현재 방어가 없어 성공합니다.**
수정 후 이 목록으로 재검증합니다.

## 우선순위 `제안`

| 순위 | 테스트 | 비용 | 덮는 위험 |
|---|---|---|---|
| 1 | ST-001 글 권한 9경우 | 매우 낮음 | 권한 우회 |
| 2 | ST-002 게시판 권한 | 매우 낮음 | 정책 회귀 |
| 3 | ST-005 SVG 강등 | 매우 낮음 | XSS |
| 4 | ST-004 계정 상태 | 낮음 | 인증 우회 |
| 5 | ST-006 오류 매핑 | 낮음 | 정보 노출 |
| 6 | ST-008 CAPTCHA 실패 | 낮음 | 스팸 |
| 7 | ST-003 토큰 수명 | 중간 (`Clock` 필요) | 계정 탈취 |
| 8 | ST-007 계정 열거 | 중간 | 정보 노출 |
| 9 | ST-009~011 DB 통합 | 중간 (Testcontainers) | 데이터 무결성 |

**1~3은 오늘 당장 쓸 수 있습니다.** 외부 의존이 전혀 없습니다.

## 작성 규칙 `제안`

기존 테스트의 관행을 따릅니다.

```java
@Test
@DisplayName("로그인한 사용자는 비회원 글을 비밀번호로 수정할 수 없다 "
           + "- 비밀번호 추측으로 남의 글을 고치는 것을 막는다")
void memberCannotEditGuestPostWithPassword() { ... }
```

무엇을 검증하는지와 **왜 그래야 하는지**를 함께 적습니다.
보안 테스트는 특히 그렇습니다 — 이유를 모르면 나중에 "불필요해 보인다"며 지워집니다.

## 관련 문서

- [threat-model.md](threat-model.md) — 위협 목록
- [security-requirements.md](security-requirements.md) — 요구사항
- [access-control.md](access-control.md) — 권한 매트릭스
- [../technology/testing-strategy.md](../technology/testing-strategy.md) — 전체 테스트 전략
- [../features/acceptance-criteria.md](../features/acceptance-criteria.md) — 인수 기준
