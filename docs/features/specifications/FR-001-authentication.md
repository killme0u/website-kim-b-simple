# FR-001 인증·인가

> 상태: `확인됨` — 코드에서 도출.
> 관련: [../use-cases/UC-001-user-signup.md](../use-cases/UC-001-user-signup.md),
> [../../security/authentication-authorization.md](../../security/authentication-authorization.md)

## 범위

| 포함 | 제외 |
|---|---|
| 세션 기반 로그인·로그아웃 + Remember-Me | 소셜 로그인 (제공 안 함, 최종) |
| CSRF 방어 | JWT·토큰 인증 (미채택) |
| 계정 상태 기반 접근 제어 | 다중 요소 인증 |
| 이메일 인증 토큰 | 임시 비밀번호 (미구현) |
| 비밀번호 재설정·아이디 찾기 | 비밀번호 변경(로그인 상태) |
| 역할 기반 권한 (`USER`/`ADMIN`) | 관리자 화면 |

## FR-001-1 인증 방식 `확인됨`

**세션 쿠키 + CSRF 토큰**을 씁니다. JWT가 아닙니다.

| 항목 | 값 | 근거 |
|---|---|---|
| 세션 저장소 | 서블릿 컨테이너 기본 (인메모리) | 별도 설정 없음 |
| 로그인 처리 URL | `POST /api/auth/login` | `config/SecurityConfig.java:44` |
| 로그아웃 URL | `POST /api/auth/logout` | `:49` |
| 요청 형식 | form 인코딩 (`username`, `password`) | `formLogin` 기본 |
| 성공 응답 | `JsonAuthenticationSuccessHandler` | `:45` |
| 실패 응답 | `JsonAuthenticationFailureHandler` | `:46` |
| 로그아웃 성공 | 204 No Content | `:51` |

### 세션 정책 `확인됨`

```java
.sessionManagement(session -> session
    .sessionFixation(fixation -> fixation.changeSessionId())
    .sessionConcurrency(concurrency -> concurrency.maximumSessions(1))
);
```

(`SecurityConfig.java:61-65`)

| 정책 | 효과 |
|---|---|
| 세션 고정 방어 | 로그인 시 세션 ID를 새로 발급 |
| 동시 세션 1개 | 같은 계정으로 다른 곳에서 로그인하면 기존 세션 만료 |

**인메모리 세션의 결과** `미결정`: 애플리케이션을 재시작하면 모든 로그인이 풀립니다.
인스턴스를 2대 이상으로 늘리면 세션 공유가 안 되어 로그인이 오락가락합니다.
수평 확장하려면 Spring Session + Redis 같은 외부 저장소가 필요합니다.

**세션 타임아웃을 설정하지 않았습니다.** 서블릿 컨테이너 기본값(보통 30분)을 씁니다.
`plan.md` 선결 결정 #4(로그인 유지 정책)가 여기에 걸려 있습니다.

## FR-001-2 CSRF 방어 `확인됨`

```java
.csrf(csrf -> csrf
    .spa()
    .ignoringRequestMatchers("/api/auth/login", "/api/members/signup")
);
```

(`SecurityConfig.java:52-56`)

`.spa()`는 두 가지를 한 번에 설정합니다.

| 구성요소 | 역할 |
|---|---|
| `CookieCsrfTokenRepository.withHttpOnlyFalse()` | 매 응답에 `XSRF-TOKEN` 쿠키를 내려줌 (JS가 읽을 수 있게) |
| `SpaCsrfTokenRequestHandler` | 헤더로 들어온 **원본** 토큰을 그대로 검증 |

### 왜 `.spa()`인가 `확인됨`

Spring Security 기본값인 `XorCsrfTokenRequestAttributeHandler`는 **마스킹된** 토큰을 기대합니다.
SPA가 쿠키 값을 그대로 헤더에 실으면 토큰이 어긋나 403이 납니다.

이것이 로그아웃 403 버그의 원인이었습니다(`todo.md` 2026-09-09).
회귀 테스트가 이 동작을 고정합니다.

| 테스트 | 확인 내용 |
|---|---|
| `writesCsrfCookieOnEveryResponse` | 모든 응답에 `XSRF-TOKEN` 쿠키가 내려간다 |
| `logoutWithoutCsrfTokenIsForbidden` | 토큰 없는 로그아웃은 403 |
| `logoutWithCookieCsrfTokenSucceeds` | 쿠키 원본 값을 헤더로 보내면 성공 |

### 클라이언트 측 `확인됨`

```ts
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});
```

(`frontend-react/src/lib/axios.ts:4-12`)

### CSRF 면제 경로 `확인됨`

| 경로 | 면제 사유 |
|---|---|
| `POST /api/auth/login` | 로그인 전에는 세션이 없어 토큰을 받을 수 없음 |
| `POST /api/members/signup` | 동일 |

두 경로 모두 CAPTCHA나 자격 증명 검증이 있어 CSRF 면제의 위험이 제한됩니다.
다만 회원가입은 CAPTCHA만이 유일한 방어선입니다.

## FR-001-3 계정 상태 기반 접근 `확인됨`

`CustomUserDetails`가 Spring Security의 표준 훅으로 상태를 매핑합니다
(`member/application/CustomUserDetails.java:41-59`).

| 메서드 | 반환 | 효과 |
|---|---|---|
| `isAccountNonExpired()` | 항상 `true` | 만료 개념 미사용 |
| `isAccountNonLocked()` | `status != SUSPENDED` | 정지 계정 로그인 차단 |
| `isCredentialsNonExpired()` | 항상 `true` | 비밀번호 만료 미사용 |
| `isEnabled()` | `status == ACTIVE \|\| status == PENDING` | 인증 전에도 로그인 허용 |

### `PENDING` 허용은 의도된 것 `확인됨`

`isEnabled()`가 `PENDING`을 명시적으로 포함합니다. 실수가 아닙니다.

그 결과 **이메일 인증이 접근을 막지 못합니다.**
`Board.checkReadable`은 로그인 여부(`actor.isEmpty()`)만 보고 `status`를 보지 않으므로
(`board/domain/Board.java:44`), `PENDING` 계정도 회원제 게시판에 들어갑니다.

인증을 실질적 관문으로 만들려면 둘 중 하나가 필요합니다. `미결정`

| 방안 | 효과 | 위험 |
|---|---|---|
| `isEnabled()`에서 `PENDING` 제외 | 인증 전 로그인 불가 | 메일이 안 가면 사용자가 완전히 막힘 |
| `Board.checkReadable`이 `status`까지 검사 | 로그인은 되나 게시판 제한 | 판정 로직이 복잡해짐 |

현재 메일 발송 문제(`todo.md`)가 미해결이므로 전자는 위험합니다.

### `isCredentialsNonExpired()`가 항상 참인 결과

`temp_password_expires_at` 컬럼이 있지만 이 훅이 보지 않습니다.
임시 비밀번호 만료가 강제되지 않습니다. `미결정`

## FR-001-4 인가 — 두 개의 층 `확인됨`

### 층 1: HTTP 경로 규칙 (`SecurityConfig`)

```
GET  /api/boards/**, /api/posts/**, /api/files/**   permitAll
     /api/auth/**                                   permitAll
     /api/members/signup                            permitAll
     /api/members/username-availability             permitAll
     /api/members/nickname-availability             permitAll
     /api/members/username-recovery                 permitAll
     /api/members/verify-email/**                   permitAll
     /api/members/password-reset/**                 permitAll
POST /api/boards/*/posts, /api/files               permitAll
PUT  /api/posts/*                                   permitAll
DEL  /api/posts/*                                   permitAll
     /api/**                                        authenticated
     그 외 (SPA 셸)                                  permitAll
```

(`SecurityConfig.java:24-41`)

### 층 2: 도메인 판정

| 판정자 | 대상 | 위치 |
|---|---|---|
| `Board.checkReadable` | 게시판 읽기 | `board/domain/Board.java:43-47` |
| `Board.checkWritable` | 게시판 쓰기 | `:49-53` |
| `Post.checkEditable` | 글 수정·삭제 | `post/domain/Post.java:75-88` |
| `CommentService` 내부 검사 | 댓글 | `comment/application/CommentService.java:64-66, 76-78` |

### 왜 쓰기가 `permitAll`인가 `확인됨`

비회원 글이 존재하기 때문입니다. "로그인했는가"만으로는
비회원이 자기 글을 비밀번호로 수정하는 흐름을 표현할 수 없습니다.

**대가**: URL만 봐서는 누가 접근 가능한지 알 수 없고,
새 엔드포인트를 추가할 때 도메인 판정을 빠뜨리면 무방비로 열립니다.

`todo.md`의 후속 검토 대상: "비회원 글쓰기가 필요 없는 게시판이 늘어나면
HTTP 레이어로 끌어올릴지 재검토 필요".

### 실제로 빠뜨린 곳 `확인됨`

`POST /api/files`가 `permitAll`인데 도메인 판정이 없습니다.
**누구나 100MB까지 업로드할 수 있습니다.** 인증도, 게시판 정책 검사도 없습니다.
→ [../../security/threat-model.md](../../security/threat-model.md) T-002

## FR-001-5 역할 `확인됨`

```java
List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()))
```

(`CustomUserDetails.java:27-29`)

| 역할 | 권한 |
|---|---|
| `USER` | 기본 |
| `ADMIN` | 모든 글·댓글 수정·삭제 |

`@EnableMethodSecurity`가 켜져 있지만(`SecurityConfig.java:19`)
`@PreAuthorize`를 쓰는 곳이 없습니다. 역할 검사는 도메인 코드에서 `MemberRole` 열거형을
직접 비교하는 방식입니다.

**관리자 계정 생성 경로가 없습니다.** `Member.pending`이 항상 `USER`로 만듭니다. `미결정`

## FR-001-6 비밀번호 `확인됨`

| 항목 | 값 |
|---|---|
| 인코더 | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` |
| 기본 알고리즘 | bcrypt (`{bcrypt}` 접두사) |
| 최소 길이 | 8자 |
| 최대 길이 | 100자 |
| 복잡도 | **요구 없음** |
| 재사용 방지 | 없음 |
| 이력 관리 | 없음 |

`DelegatingPasswordEncoder`는 해시에 알고리즘 접두사를 붙여 저장하므로
나중에 알고리즘을 바꿔도 기존 비밀번호가 계속 검증됩니다.

비회원 글 비밀번호도 같은 인코더로 해시합니다(`post/application/PostService.java:81`).
다만 **길이 제약이 없습니다** — `PostCommand.guestPassword`에 검증 애너테이션이 없습니다. `미결정`

## FR-001-7 인증 실패 응답 `확인됨`

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
    .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.FORBIDDEN))
);
```

(`SecurityConfig.java:57-60`)

**본문 없이 상태 코드만** 반환합니다. 로그인 페이지로 리다이렉트하지 않습니다.
SPA에 적합한 선택입니다.

도메인 층의 `AuthenticationRequiredException`은 본문이 있는 401을 냅니다.
따라서 401이 두 형태로 나옵니다 — [../error-policy.md](../error-policy.md) 참조.

## FR-001-8 클라이언트 세션 동기화 `확인됨`

```ts
api.interceptors.response.use(
  response => response,
  (error) => {
    if (isUnauthorized(error)) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);
```

(`frontend-react/src/lib/axios.ts:26-34`)

401이 오면 전역 인터셉터가 클라이언트 상태를 비웁니다.
**서버가 단일 진실 원천**이고 클라이언트는 따라갑니다.
라우트 가드가 따로 없는 이유입니다.

`authStore`는 `user`, `isAuthenticated`, `isAdmin` 셋을 함께 갱신해
파생 상태가 어긋나지 않게 합니다(`store/authStore.ts:16-21`).

## 미충족 요구 요약 `미결정`

| 항목 | 상태 |
|---|---|
| HTTPS 강제 | 설정 없음 |
| 쿠키 `Secure`·`SameSite` | 지정 없음 |
| 로그인 실패 횟수 제한 | 없음 — 무차별 대입에 무방비 |
| 세션 타임아웃 명시 | 컨테이너 기본값 |
| 비밀번호 변경 시 세션 무효화 | 없음 |
| 로그인 시 CAPTCHA | 없음 |
| 감사 로그 | 없음 |
| 관리자 계정 생성 | 없음 |

## 관련 문서

- [../../security/authentication-authorization.md](../../security/authentication-authorization.md) — 보안 관점 상세
- [../../security/access-control.md](../../security/access-control.md) — 권한 매트릭스
- [../use-cases/UC-001-user-signup.md](../use-cases/UC-001-user-signup.md) — 가입 흐름
- [../state-machines.md](../state-machines.md) — 계정 상태 전이
