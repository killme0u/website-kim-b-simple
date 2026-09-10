# 보안 아키텍처

> 상태: `확인됨` — 코드에 존재하는 방어 계층을 그렸습니다.

## 방어 계층 `확인됨`

```
┌─────────────────────────────────────────────────────────────┐
│ 0. 전송 계층                                                  │
│    HTTPS 강제 없음 · 쿠키 Secure/SameSite 없음                 │
│    ★ 비어 있음 — T-001                                        │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Spring Security 필터 체인                                  │
│    ├─ CsrfFilter          변경 요청 토큰 검증 → 403           │
│    ├─ 세션 조회            JSESSIONID → SecurityContext       │
│    │    isAccountNonLocked()  SUSPENDED 차단                  │
│    │    isEnabled()           DELETED 차단, PENDING 허용       │
│    └─ AuthorizationFilter  경로 규칙 → 401                    │
│         ※ 대부분 permitAll                                    │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. 컨트롤러 — 형식 검증                                        │
│    @Valid  길이·필수·이메일 형식 → 400                         │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 도메인 — 권한 판정  ★ 실질적 방어선                          │
│    Board.checkReadable / checkWritable  → 401                │
│    Post.checkEditable                   → 403                │
│    CommentService 내부 검사              → 401 / 403          │
│    CaptchaVerifier.verify               → 400                │
│    VerificationToken.isUsable           → 400                │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. DB 제약 — 최종 방어선                                       │
│    UNIQUE (username, email, nickname 부분, stored_name, …)   │
│    CHECK  (post_author_ck)                                   │
│    복합 PK (post_like, post_view_log)                         │
│    → DataIntegrityViolationException → 409                   │
└─────────────────────────────────────────────────────────────┘
```

## 계층 1 — HTTP 경로 규칙 `확인됨`

```java
GET  /api/boards/**, /api/posts/**, /api/files/**   permitAll
     /api/auth/**, /api/members/signup,
     /api/members/*-availability,
     /api/members/username-recovery,
     /api/members/verify-email/**,
     /api/members/password-reset/**                 permitAll
POST /api/boards/*/posts, /api/files                permitAll
PUT  /api/posts/*                                   permitAll
DEL  /api/posts/*                                   permitAll
     /api/**                                        authenticated
     anyRequest()                                   permitAll   ← SPA 셸
```

(`config/SecurityConfig.java:24-41`)

### 이 계층이 실제로 막는 것 `확인됨`

`authenticated()`에 걸리는 것은 위 목록에 없는 `/api/**`뿐입니다.

| 경로 | 막힘 |
|---|---|
| `GET /api/me` | 예 |
| `GET /api/me/posts`, `/api/me/comments` | 예 |
| `POST /api/posts/*/like` | 예 |
| `POST/PUT/DELETE /api/posts/*/comments`, `/api/comments/*` | 예 |

나머지는 전부 계층 3에 위임됩니다.

### `anyRequest().permitAll()`의 위험 `미결정`

SPA 셸(정적 리소스, 딥링크)을 열기 위한 것입니다.
`SecurityConfigTest.spaShellIsReachableAnonymously`가 이 동작을 검증합니다.

**부작용**: `/api`로 시작하지 않는 새 엔드포인트는 자동으로 공개됩니다.
예를 들어 Actuator를 추가하면 `/actuator/**`가 그대로 열립니다.
→ [../technology/infrastructure/observability.md](../technology/infrastructure/observability.md)

## 계층 3 — 도메인 판정이 핵심인 이유 `확인됨`

**비회원 글이 존재하기 때문입니다.**

"로그인했는가"만으로는 다음을 표현할 수 없습니다.

- 비회원이 비밀번호로 자기 글을 수정
- 게시판마다 다른 읽기·쓰기 정책
- 관리자의 전체 권한

그래서 판정이 도메인 객체로 내려갔습니다.

```java
// Board — 게시판 정책
public void checkReadable(Optional<Member> actor) {
    if (this.requiresAuthToRead && actor.isEmpty()) {
        throw new AuthenticationRequiredException(...);
    }
}

// Post — 소유 판정
public void checkEditable(Optional<Member> actor, String rawGuestPassword, PasswordEncoder encoder) {
    if (actor.isPresent()) {
        if (m.getRole() == MemberRole.ADMIN) return;
        if (this.member != null && this.member.getId().equals(m.getId())) return;
        throw new AccessDeniedException("작성자만 수정·삭제할 수 있습니다.");
    }
    if (this.member != null) throw new AccessDeniedException(...);
    if (rawGuestPassword == null || !encoder.matches(rawGuestPassword, this.guestPasswordHash)) {
        throw new AccessDeniedException(...);
    }
}
```

### 이 설계의 장점 `확인됨`

| 장점 | 내용 |
|---|---|
| 표현력 | 복잡한 정책을 자연스럽게 표현 |
| 테스트 용이 | 순수 객체라 의존성 없이 검증 가능 |
| 정책의 단일 위치 | 모든 호출 경로가 같은 메서드를 씀 |

### 이 설계의 위험 `확인됨`

**새 엔드포인트에서 판정 호출을 빠뜨리면 무방비로 열립니다.**
URL만 봐서는 누가 접근 가능한지 알 수 없습니다.

실제 사례: `POST /api/files`가 `permitAll`인데 도메인 판정도 없습니다.
누구나 업로드할 수 있습니다(T-002).

`todo.md`에도 이 구조에 대한 재검토가 후속 과제로 등록되어 있습니다.

### 완화 방안 `제안`

새 API 체크리스트에 "권한 판정이 계층 1 또는 3에 있는가"를 넣습니다.
→ [../technology/api/api-guidelines.md](../technology/api/api-guidelines.md) G-007

## 계층 4 — DB 제약을 방어선으로 삼는 설계 `확인됨`

`PRD.md` 2.4의 원칙입니다 — **중복 확인 API는 편의 기능이고 방어선은 DB 제약**입니다.

| 제약 | 막는 것 |
|---|---|
| `member.username` UNIQUE | 아이디 중복 |
| `member.email` UNIQUE | 이메일 중복 |
| `ux_member_nickname` 부분 UNIQUE | 닉네임 중복 (NULL 제외) |
| `verification_token.token_hash` UNIQUE | 토큰 충돌 |
| `attachment.stored_name` UNIQUE | UUID 충돌 |
| `post_author_ck` CHECK | 회원/비회원 글 혼재 |
| `post_like` 복합 PK | 좋아요 중복 |
| `post_view_log` 복합 PK | 조회수 중복 |

애플리케이션이 버그를 내도 DB가 막습니다.

### 조건 — 조회와 저장의 정규화가 같아야 한다 (결정 D5) `확인됨`

중복 확인이 통과했는데 저장에서 409가 나면 UX 오류로 드러납니다.
그래서 규칙을 `Member.normalizeNickname` **한 곳에만** 둡니다.

```java
public static String normalizeNickname(String nickname) {
    if (nickname == null) return null;
    String trimmed = nickname.trim();
    return trimmed.isEmpty() ? null : trimmed;
}
```

조회(`MemberController.checkNickname`)와 저장(`Member.pending`)이 모두 이것을 씁니다.
회귀 테스트: `MemberTest`, `MemberControllerTest`.

**아이디는 아직 이 규칙이 없습니다** — 조회도 저장도 정규화하지 않아
현재는 일치하지만 앞뒤 공백이 든 아이디가 만들어질 수 있습니다. `미결정`

## 비밀 취급 `확인됨`

### 저장하지 않는 것

| 값 | 저장 형태 |
|---|---|
| 회원 비밀번호 | bcrypt 해시 |
| 비회원 글 비밀번호 | bcrypt 해시 |
| 인증·재설정 토큰 | SHA-256 해시 (원문 미저장) |
| CAPTCHA 토큰 | **저장 안 함** — 검증 후 폐기 |

토큰 원문은 메일 링크에만 존재합니다.
DB가 유출돼도 인증 링크를 복원할 수 없습니다.

### 클라이언트 측 `확인됨`

`plan.md`의 규칙 — "원본 CAPTCHA 토큰과 비밀번호는
로그·localStorage·Zustand persist에 남기지 않는다".

`authStore`는 `persist` 미들웨어를 쓰지 않습니다
(`store/authStore.ts` — `create()`만 사용). 새로고침하면 상태가 초기화되고
`useSessionSync`가 `GET /api/me`로 서버에서 다시 받아옵니다.

**서버가 단일 진실 원천**이라는 원칙이 보안에도 유리하게 작용합니다.

## CSRF 아키텍처 `확인됨`

```
서버 → 모든 응답에 Set-Cookie: XSRF-TOKEN=<원본토큰>  (httpOnly=false)
클라이언트 → 변경 요청에 X-XSRF-TOKEN: <같은 값>
서버 → SpaCsrfTokenRequestHandler 가 원본 그대로 비교
```

`httpOnly=false`가 **의도된 설계**입니다 — JS가 쿠키를 읽어야 헤더에 실을 수 있습니다.
XSS가 발생하면 토큰도 읽히지만, XSS 상황에서는 CSRF 방어가 이미 무의미합니다.

### 왜 기본값이 아닌 `.spa()`인가 `확인됨`

기본 `XorCsrfTokenRequestAttributeHandler`는 **마스킹된** 토큰을 기대합니다.
SPA가 쿠키 값을 그대로 보내면 어긋나 403이 납니다.

이것이 로그아웃 403 버그의 원인이었고(`todo.md` 2026-09-09),
회귀 테스트 3개가 이 결정을 고정합니다.

## 계정 열거 방지 `확인됨`

메일 관련 API 3종이 계정 유무와 무관하게 같은 응답을 냅니다.

```java
memberRepository.findByEmail(email)
        .filter(member -> member.getStatus() == MemberStatus.PENDING)
        .ifPresent(this::issueEmailVerification);
// → 항상 202
```

`Optional.ifPresent`로 처리해 없으면 조용히 아무것도 하지 않습니다.

**의도된 예외**: 아이디·닉네임 중복 확인은 존재 여부를 그대로 노출합니다.
가입 UX와의 트레이드오프이며 `PRD.md` 2.4가 명시적으로 받아들였습니다.

## 실패 시 동작 방향 `확인됨`

| 컴포넌트 | 실패 시 | 방향 |
|---|---|---|
| CAPTCHA (네트워크·오류·타임아웃) | `false` | **fail-closed** (안전) |
| CAPTCHA (미설정) | `false` | fail-closed |
| SMTP (미설정) | 로그 전용 발송기 | **fail-open** (가용성 우선) |
| SMTP (발송 실패) | 로그만, 가입은 성공 | fail-open |
| Flyway (검증 실패) | 기동 실패 | **fail-closed** (안전) |

**CAPTCHA는 닫고, 메일은 여는** 선택입니다.
CAPTCHA는 방어 수단이라 열면 위험하고,
메일은 부가 기능이라 닫으면 가입 자체가 막히기 때문입니다.

일관된 판단입니다. 다만 CAPTCHA fail-closed는
**provider 장애 = 가입 전면 중단**을 뜻하고,
실패 로그가 없어 원인 파악이 불가능합니다. `미결정`

## 비어 있는 계층 `확인됨`

| 계층 | 상태 |
|---|---|
| **0. 전송 (TLS)** | **없음** — 가장 큰 공백 |
| 속도 제한 | 없음 |
| WAF | 없음 |
| 감사 로그 | 없음 |
| 침입 탐지 | 없음 |
| 보안 헤더 (CSP, nosniff, HSTS) | 없음 |

계층 0이 비어 있으면 위쪽 계층이 아무리 튼튼해도
세션이 가로채이는 순간 전부 무의미해집니다.

## 관련 문서

- [threat-model.md](threat-model.md) — 위협별 방어 상태
- [authentication-authorization.md](authentication-authorization.md) — 인증·인가 상세
- [access-control.md](access-control.md) — 권한 매트릭스
- [../technology/architecture/data-flow.md](../technology/architecture/data-flow.md) — 요청 흐름
