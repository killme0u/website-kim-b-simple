# 컴포넌트 (C4 Level 3)

> 상태: `확인됨` — 실제 패키지·클래스 구조입니다.

## 백엔드 패키지 구조 `확인됨`

`page.sanotehu.board.backend` 아래 도메인별로 나뉩니다.

```
backend
├── BoardApplication.java          진입점
├── member/
│   ├── adapter/in/web/
│   │   ├── AuthController              GET /api/me
│   │   ├── MemberController            가입·인증·중복확인·복구
│   │   ├── MyPageController            내 글·내 댓글
│   │   └── dto/  SignupCommand, MeResponse, PasswordReset*, UsernameRecovery*
│   ├── adapter/out/mail/
│   │   ├── AppMailProperties           app.mail.* 바인딩
│   │   ├── SmtpMailSender              실제 발송
│   │   └── LoggingMailSender           폴백
│   ├── application/
│   │   ├── SignupService               가입 유스케이스
│   │   ├── VerificationService         인증·재설정 토큰
│   │   ├── UsernameRecoveryService     아이디 찾기
│   │   ├── SignupMailListener          이벤트 → 메일
│   │   ├── MailSenderPort              포트
│   │   ├── TokenHasher                 SHA-256
│   │   ├── CustomUserDetails(Service)  Spring Security 연결
│   │   └── SignupCompleted / EmailVerificationRequested /
│   │       PasswordResetRequested / UsernameRecoveryRequested   (이벤트)
│   └── domain/
│       ├── Member, MemberStatus, MemberRole
│       ├── VerificationToken
│       └── MemberRepository, VerificationTokenRepository
├── board/       adapter/in/web + application + domain
├── post/        adapter/in/web + application + domain
├── comment/     adapter/in/web + application + domain
├── attachment/  adapter/in/web + application + domain
├── captcha/
│   ├── application/CaptchaVerifier          포트
│   └── adapter/out/ConfiguredCaptchaVerifier, CaptchaProperties
├── common/
│   ├── ApiError, GlobalExceptionHandler
│   ├── AuthenticationRequiredException, UnsupportedFileTypeException
│   └── BaseTimeEntity
└── config/
    ├── SecurityConfig, SpaResourceConfig
    ├── MailConfig, CaptchaConfig
    ├── AsyncConfig, JpaAuditingConfig
    └── JsonAuthenticationSuccessHandler / FailureHandler
```

## 도메인별 컴포넌트

### member — 가장 큰 도메인 `확인됨`

컨트롤러가 셋으로 나뉩니다.

| 컨트롤러 | 경로 | 책임 |
|---|---|---|
| `AuthController` | `/api/me` | 현재 사용자 조회만 |
| `MemberController` | `/api/members/**` | 가입, 인증, 중복확인, 복구 |
| `MyPageController` | `/api/me/**` | 내 글·내 댓글 |

`AuthController`와 `MyPageController`가 같은 `/api/me` 접두사를 나눠 씁니다.

서비스는 유스케이스별로 셋입니다.

| 서비스 | 책임 |
|---|---|
| `SignupService` | 가입만 |
| `VerificationService` | 이메일 인증 + 비밀번호 재설정 |
| `UsernameRecoveryService` | 아이디 찾기만 |

**`MemberController`가 `MemberRepository`를 직접 씁니다** (중복 확인 API).
서비스를 거치지 않는 유일한 예외입니다(`member/adapter/in/web/MemberController.java:35, 76, 89`).
조회만 하고 규칙이 `Member.normalizeNickname` 한 곳에 있어 계층을 건너뛴 것으로 보입니다.

### 이벤트 기반 메일 발송 `확인됨`

```
SignupService ──publishEvent──► SignupCompleted
                                      │
                          @TransactionalEventListener(AFTER_COMMIT)
                          @Async
                                      ▼
                             SignupMailListener
                                      │
                                      ▼
                              MailSenderPort  (인터페이스)
                                 ╱        ╲
                     SmtpMailSender    LoggingMailSender
```

이벤트 4종이 모두 `SignupMailListener` 하나로 모입니다.

| 이벤트 | 발행처 | 템플릿 |
|---|---|---|
| `SignupCompleted` | `SignupService` | `mail/email-verification` |
| `EmailVerificationRequested` | `VerificationService` (재발송) | `mail/email-verification` |
| `PasswordResetRequested` | `VerificationService` | `mail/password-reset` |
| `UsernameRecoveryRequested` | `UsernameRecoveryService` | `mail/username-recovery` |

`AsyncConfig`가 `@Async` 실행기를 제공합니다.

### board — 정책의 보유자 `확인됨`

가장 작지만 가장 많이 호출됩니다.

```java
public void checkReadable(Optional<Member> actor) { ... }
public void checkWritable(Optional<Member> actor) { ... }
```

이 두 메서드를 부르는 곳:

| 호출자 | 메서드 |
|---|---|
| `BoardService.getBoardBySlug` | `checkReadable` |
| `PostService.listPosts` | `checkReadable` |
| `PostService.getPost` | `checkReadable` |
| `PostService.createPost` | `checkWritable` |
| `CommentService.getComments` | `checkReadable` |
| `CommentService.createComment` | `checkWritable` |

**게시판 목록(`BoardService.getAllBoards`)만 검사하지 않습니다** — 의도된 것입니다.

### post — 가장 복잡한 서비스 `확인됨`

`PostService`가 리포지토리 5개에 의존합니다.

| 의존 | 용도 |
|---|---|
| `PostRepository` | 글 CRUD, 원자적 카운터 |
| `BoardRepository` | 정책 조회 |
| `PostViewLogRepository` | 조회수 중복 방지 |
| `PostLikeRepository` | 좋아요 |
| `AttachmentRepository` | 첨부 |
| `PasswordEncoder` | 비회원 비밀번호 |

**`PostService`가 `attachment` 도메인의 리포지토리를 직접 씁니다.**
도메인 경계를 넘는 지점입니다. 첨부는 글에 종속적이라 실용적 선택이지만,
`attachment`에 규칙이 생기면 `PostService`가 그것을 모릅니다
(실제로 `allowsAttachment` 미검증이 여기서 나옵니다). `미결정`

### 원자적 카운터 `확인됨`

```java
@Modifying(clearAutomatically = true)
@Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
void increaseViewCount(@Param("id") Long id);
```

읽고-더하고-쓰는 방식이 아니라 DB에서 직접 증가시킵니다.
`clearAutomatically = true`가 영속성 컨텍스트를 비워 stale 값을 막습니다.

`decreaseLikeCount`는 `and p.likeCount > 0` 조건이 붙어 음수를 막습니다.

### captcha — 포트와 어댑터가 분리된 예 `확인됨`

```
application/CaptchaVerifier          (인터페이스, 도메인 쪽)
        ▲
        │ implements
adapter/out/ConfiguredCaptchaVerifier (java.net.http.HttpClient 사용)
adapter/out/CaptchaProperties         (app.captcha.* 바인딩)
config/CaptchaConfig                  (빈 등록)
```

모드에 따라 동작이 갈립니다.

| 모드 | 동작 |
|---|---|
| `fake` | `expectedToken`과 문자열 비교 |
| `remote` | HTTP POST 후 `success` 확인 |
| 그 외/미설정 | 무조건 `false` |

모든 예외를 `false`로 흡수합니다 — 안전한 방향이지만
**실패 원인을 로그로도 남기지 않습니다**. `미결정`

### common `확인됨`

| 클래스 | 역할 |
|---|---|
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 예외 6종 → HTTP |
| `ApiError` | `{ code, message }` |
| `AuthenticationRequiredException` | 401용 커스텀 예외 |
| `UnsupportedFileTypeException` | 415용 커스텀 예외 |
| `BaseTimeEntity` | `createdAt`/`updatedAt` (JPA Auditing) |

`BaseTimeEntity`를 상속하는 것은 `Member`, `Post`입니다.
`Comment`는 자체 필드를 가집니다.

## 프론트엔드 구조 `확인됨`

```
frontend-react/src/
├── main.tsx / App.tsx          라우트 정의
├── layouts/RootLayout.tsx      공통 레이아웃, 네비게이션
├── pages/                      화면 10개
│   ├── HomePage, LoginPage, SignupPage, MyPage
│   ├── BoardPage, PostPage, PostEditPage
│   └── FindUsernamePage, FindPasswordPage, VerifyEmailPage
├── components/
│   ├── CaptchaField.tsx        CAPTCHA 위젯
│   ├── LoginRequired.tsx       비로그인 안내
│   └── ui/                     COSS UI 컴포넌트 50여 개
├── lib/
│   ├── axios.ts                API 클라이언트 + CSRF + 401 인터셉터
│   ├── session.ts              useSessionSync
│   ├── utils.ts, segmented-control.ts
├── hooks/                      use-media-query, use-mobile
├── store/authStore.ts          Zustand — 인증 상태만
├── shared/ui/index.tsx
└── types.ts
```

### 상태 관리의 분리 `확인됨`

| 종류 | 도구 | 대상 |
|---|---|---|
| 서버 상태 | TanStack Query | 게시글, 댓글, 게시판 |
| 인증 상태 | Zustand (`authStore`) | `user`, `isAuthenticated`, `isAdmin` |
| 목록 상태 | URL 쿼리 | 페이지, 검색어 |
| 폼 상태 | React Hook Form + Zod | 입력 |

`authStore`가 유일한 Zustand 스토어입니다.
`PRD.md` 11장(상태 관리 판단 트리)의 기준을 따른 결과로 보입니다.

```ts
setUser: user => set({
  user,
  isAuthenticated: user !== null,
  isAdmin: user?.role === 'ADMIN',
})
```

파생 상태를 함께 갱신해 어긋날 수 없게 했습니다.

### API 클라이언트의 두 가지 역할 `확인됨`

`lib/axios.ts` 하나가 CSRF와 세션 동기화를 모두 담당합니다.

1. `withXSRFToken` + 쿠키·헤더 이름 지정 → CSRF 자동 처리
2. 응답 인터셉터에서 401 감지 → `authStore.logout()`

라우트 가드가 없는 이유입니다 — 서버가 401을 주면 상태가 따라옵니다.

## 컴포넌트 간 결합도 `제안`

| 결합 | 평가 |
|---|---|
| `PostService` → `AttachmentRepository` | 도메인 경계 침범. 정책 검사 누락의 원인 |
| `MemberController` → `MemberRepository` | 계층 건너뜀. 조회 전용이라 위험은 낮음 |
| `CommentService` → `PostRepository` | 자연스러움 (댓글은 글에 종속) |
| `SignupMailListener` → 이벤트 4종 | 응집도 높음. 메일 발송이 한 곳 |
| `Board` → 모든 서비스 | 정책 중앙화. 의도된 설계 |

## 관련 문서

- [data-flow.md](data-flow.md) — 요청 흐름
- [../adr/ADR-001-modular-monolith.md](../adr/ADR-001-modular-monolith.md) — 구조 결정
- [../developer-guide.md](../developer-guide.md) — 코드 수정 시작점
- [../../features/feature-catalog.md](../../features/feature-catalog.md) — 기능별 위치
