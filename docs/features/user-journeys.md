# 사용자 여정

> 상태: `확인됨` — 각 단계가 실제로 호출하는 API와 판정 지점을 코드에서 확인했습니다.
> 화면 흐름은 `frontend-react/src/App.tsx`와 `UI.md` 기준입니다.

## J-001 비회원의 자유게시판 이용

가장 마찰이 적은 경로입니다. 로그인 없이 글까지 씁니다.

```
홈(/) 
  └─ GET /api/boards                      누구나 가능
       └─ 자유게시판 클릭 → /boards/free
            └─ GET /api/boards/free       requires_auth_to_read=false → 통과
            └─ GET /api/boards/free/posts 통과
                 └─ 글 클릭 → /posts/{id}
                      └─ GET /api/posts/{id}
                           조회수 +1 (비회원은 매번 증가)
                 └─ 글쓰기 → /boards/free/posts/new
                      └─ POST /api/boards/free/posts
                           requires_auth_to_write=false → 통과
                           guestNickname + guestPassword 필수
```

### 비회원이 자기 글을 수정할 때

```
/posts/{id} → 수정 클릭
  └─ 비밀번호 입력 프롬프트
       └─ PUT /api/posts/{id}  { title, content, guestPassword }
            Post.checkEditable:
              로그인 안 함 → 비회원 글인가? → 예
                → encoder.matches(guestPassword, hash)
                    일치 → 수정
                    불일치 → 403 FORBIDDEN
```

**주의**: 로그인한 상태에서는 이 경로가 막힙니다. `Post.checkEditable`이
로그인 분기로 들어가 작성자 일치만 보기 때문입니다(`post/domain/Post.java:76-81`).
비회원 글을 쓴 뒤 로그인하면 자기 글을 고칠 수 없습니다. `미결정`

### 비회원이 막히는 지점 `확인됨`

| 시도 | 결과 |
|---|---|
| Q&A·자료실 목록 열람 | 401 `AUTH_REQUIRED` (`V4` 이후) |
| 댓글 작성 | 401 `AUTH_REQUIRED` |
| 좋아요 | 401 `AUTH_REQUIRED` |
| 마이페이지 | 401 |

## J-002 신규 회원 가입 → 활성화

가장 긴 여정이며, 현재 **가장 취약한 지점**입니다.

```
/signup
  1) 약관 동의        termsAccepted 체크 (※ 서버에서 검증하지 않음)
  2) 기본 정보 입력    username, password, name, nickname, email, phone
  3) 중복 확인
       GET /api/members/username-availability?username=...
       GET /api/members/nickname-availability?nickname=...
  4) CAPTCHA          CaptchaField에서 토큰 획득
  5) 제출
       POST /api/members/signup
         └─ CaptchaVerifier.verify()      실패 시 400, 회원 생성 안 됨
         └─ Member.pending()               status = PENDING
         └─ VerificationToken 발급          SHA-256 해시만 저장, 24시간
         └─ SignupCompleted 이벤트 발행
       ← 201 { "id": 123 }
       
       [트랜잭션 커밋]
       
       └─ SignupMailListener (AFTER_COMMIT, @Async)
            └─ 인증 메일 발송 → APP_BASE_URL/verify-email?token=원문토큰
```

### 메일 발송이 커밋 이후인 이유 `확인됨`

`@TransactionalEventListener(phase = AFTER_COMMIT)`이므로
**메일 실패가 가입 트랜잭션을 되돌리지 않습니다**
(`member/application/SignupMailListener.java:36`).

반대급부: 메일이 실패해도 사용자는 201을 받습니다.
`send()`가 예외를 잡아 `log.error`로만 남기므로(`SignupMailListener.java:78-81`),
호출자도 사용자도 실패를 알 수 없습니다.

이것이 `todo.md`의 "발송된 메일이 없음"이 조용히 발생하는 구조적 이유입니다.

### 인증 단계

```
메일의 링크 클릭 → /verify-email?token=xxx
  └─ VerifyEmailPage
       └─ GET /api/members/verify-email?token=xxx
            └─ 해시 조회 → isUsable() 확인
                 유효    → Member.verifyEmail()  PENDING → ACTIVE
                          token.markUsed()
                          ← 200 { "status": "verified" }
                 만료·사용됨 → 400 BAD_REQUEST
                 없는 토큰   → 400 BAD_REQUEST
```

### 인증 전에 할 수 있는 것 `확인됨`

`PENDING` 상태에서도 **로그인은 됩니다.** 우연이 아니라 명시적인 설계입니다 —
`CustomUserDetails.isEnabled()`가 `ACTIVE`와 `PENDING` 둘 다 참으로 돌려줍니다
(`member/application/CustomUserDetails.java:57-59`).

Spring Security가 계정 상태로 로그인을 막는 지점은 두 곳이며, 실제로 동작합니다.

| 메서드 | 거부하는 상태 | 근거 |
|---|---|---|
| `isAccountNonLocked()` | `SUSPENDED` | `CustomUserDetails.java:47-49` |
| `isEnabled()` | `DELETED` (및 그 외 상태) | `CustomUserDetails.java:57-59` |

그리고 게시판 입장 판정은 로그인 여부만 봅니다 —
`Board.checkReadable`은 `actor.isEmpty()`만 확인하고 `status`는 보지 않습니다
(`board/domain/Board.java:44`).

두 사실을 합치면: **이메일 인증을 마치지 않아도 Q&A·자료실을 이용할 수 있습니다.**
`PENDING`으로 로그인이 되고, 게시판은 로그인 여부만 보기 때문입니다. `미결정`

인증이 실제로 막는 것은 현재 없습니다. `PRD.md`의 의도와 다를 수 있어 확인이 필요합니다.
막으려면 `isEnabled()`에서 `PENDING`을 빼거나, `Board.checkReadable`이 `status`까지 보게 해야 합니다.
전자는 인증 메일이 안 갈 때 사용자가 로그인조차 못 하게 되므로,
현재 메일 발송 문제(`todo.md`)가 해결된 뒤에 바꾸는 편이 안전합니다.

## J-003 기존 회원의 Q&A 이용

```
/login
  └─ POST /api/auth/login (form)  username, password
       └─ JsonAuthenticationSuccessHandler → 200
       └─ 세션 쿠키 발급 + XSRF-TOKEN 쿠키
  └─ GET /api/me → authStore.setUser()

/boards/qna
  └─ GET /api/boards/qna         checkReadable 통과 (로그인됨)
  └─ GET /api/boards/qna/posts
       └─ /posts/{id}
            └─ GET /api/posts/{id}
                 post_view_log INSERT ON CONFLICT DO NOTHING
                   1행 → view_count +1
                   0행 → 오늘 이미 봤음, 증가 없음
            └─ GET /api/posts/{id}/comments
            └─ POST /api/posts/{id}/comments   allowsComment=true라 통과
            └─ POST /api/posts/{id}/like        토글
```

### CSRF 흐름 `확인됨`

```
모든 응답 → Set-Cookie: XSRF-TOKEN=<원본토큰>   (httpOnly=false)
변경 요청 → X-XSRF-TOKEN: <같은 값>
```

axios가 `withXSRFToken: true`로 자동 처리합니다(`lib/axios.ts:9-11`).
서버는 `csrf().spa()`를 쓰므로 마스킹되지 않은 원본 토큰을 기대합니다
(`config/SecurityConfig.java:52-56`).

기본 `XorCsrfTokenRequestAttributeHandler`였을 때 로그아웃이 403 나던 버그의 원인이 이것입니다
(`todo.md` 2026-09-09, 회귀 테스트 `SecurityConfigTest`).

**예외**: 로그인과 회원가입은 CSRF 검사에서 제외됩니다
(`ignoringRequestMatchers("/api/auth/login", "/api/members/signup")`).

## J-004 비밀번호를 잊었을 때

```
/login → 비밀번호 찾기 → /find-password
  └─ 이메일 + CAPTCHA 입력
       └─ POST /api/members/password-reset/request
            └─ CaptchaVerifier.verify()   실패 → 400
            └─ 이메일로 회원 조회
                 있고 DELETED 아님 → PASSWORD_RESET 토큰 발급 (1시간)
                                     PasswordResetRequested 이벤트
                 없음               → 아무것도 안 함
            ← 202 { "status": "requested" }   ※ 두 경우 응답 동일
       
       [커밋 후] 메일 발송 → APP_BASE_URL/find-password?token=xxx

메일 링크 클릭 → /find-password?token=xxx
  └─ 새 비밀번호 입력
       └─ POST /api/members/password-reset/change  { token, newPassword }
            └─ isUsable("PASSWORD_RESET") 확인
                 유효 → changePassword() 
                        mustChangePassword = false
                        tempPasswordExpiresAt = null
                        token.markUsed()
                 ← 200 { "status": "changed" }
```

계정 유무와 무관하게 202를 반환하는 것이 계정 열거 방지입니다
(`member/application/VerificationService.java:58-60`).

## J-005 아이디를 잊었을 때

```
/find-username
  └─ 이메일 입력
       └─ POST /api/members/username-recovery
            └─ 이메일로 조회, DELETED 아니면 UsernameRecoveryRequested 발행
            ← 202 "가입 이메일로 아이디 안내를 전송했습니다."
       [커밋 후] 메일에 username 평문 포함
```

**본인 확인이 이메일 소유뿐입니다.** CAPTCHA도 없습니다.
`plan.md` 선결 결정 #5가 이 방식을 확정 대상으로 남겨두었습니다. `미결정`

가입·비밀번호 재설정에는 CAPTCHA가 있는데 아이디 찾기에는 없는 비대칭입니다.

## J-006 세션이 끊겼을 때

```
아무 API 호출 → 401
  └─ api.interceptors.response
       └─ useAuthStore.getState().logout()
            user=null, isAuthenticated=false, isAdmin=false
  └─ 화면이 비로그인 상태로 전환
```

전역 인터셉터 하나가 처리합니다(`lib/axios.ts:26-34`).
비로그인 상태에서 발생한 401은 이미 로그아웃 상태라 아무 효과가 없습니다.

## 여정별 이탈 위험 `제안`

| 여정 | 이탈 지점 | 원인 |
|---|---|---|
| J-002 | 인증 메일 대기 | 메일 미도달 시 사용자가 알 방법 없음 |
| J-002 | 중복 확인 | 두 필드 모두 확인해야 제출 가능 |
| J-002 | CAPTCHA | 운영 provider 미확정 |
| J-004 | 1시간 만료 | 메일을 늦게 열면 재요청 필요 |
| J-001 | 비회원 글 수정 | 로그인 상태면 자기 글을 못 고침 |

## 관련 문서

- [use-cases/UC-001-user-signup.md](use-cases/UC-001-user-signup.md) — J-002 상세
- [state-machines.md](state-machines.md) — 상태 전이
- [error-policy.md](error-policy.md) — 각 실패 지점의 응답
