# 데이터 흐름

> 상태: `확인됨` — 코드 경로를 따라 그렸습니다.

## DF-001 일반 API 요청

```
브라우저
  │  fetch/axios  (withCredentials, X-XSRF-TOKEN)
  ▼
[개발] Vite :5173 ──proxy──► :8080
[운영] 직접 :8080
  ▼
Spring Security 필터 체인
  ├── CsrfFilter          변경 요청이면 토큰 검증 → 실패 시 403 (본문 없음)
  ├── 세션 조회            JSESSIONID → SecurityContext
  └── AuthorizationFilter  경로 규칙 → 실패 시 401 (본문 없음)
  ▼
SpaResourceConfig         /api/ 로 시작하면 통과, 아니면 정적 리소스
  ▼
DispatcherServlet → Controller
  ├── @Valid              실패 → MethodArgumentNotValidException → 400
  └── @AuthenticationPrincipal CustomUserDetails   (비로그인이면 null)
  ▼
Service (@Transactional)
  ▼
Domain 권한 판정
  ├── Board.checkReadable / checkWritable   → AuthenticationRequiredException → 401
  └── Post.checkEditable                    → AccessDeniedException → 403
  ▼
Repository → PostgreSQL
  │           UNIQUE 위반 → DataIntegrityViolationException → 409
  ▼
Response DTO → JSON
```

**응답이 나가는 모든 경로에 `XSRF-TOKEN` 쿠키가 실립니다** (`csrf().spa()`).

## DF-002 회원가입 — 트랜잭션 경계가 핵심

```
POST /api/members/signup
  │
  ├─[트랜잭션 시작]─────────────────────────────────┐
  │                                                │
  │  1. CaptchaVerifier.verify(token, remoteAddr)  │
  │       ├─ fake:   expectedToken 과 문자열 비교    │
  │       └─ remote: HTTP POST → success 확인       │
  │       실패 → IllegalArgumentException          │
  │              → 롤백, 회원 생성 안 됨 → 400       │
  │                                                │
  │  2. Member.pending(...)                        │
  │       nickname = normalizeNickname(nickname)   │
  │       password = encoder.encode(password)      │
  │       status = PENDING                         │
  │                                                │
  │  3. memberRepository.save()                    │
  │       UNIQUE 위반 → 롤백 → 409                  │
  │                                                │
  │  4. rawToken = UUID.randomUUID()               │
  │     tokenRepository.save(                      │
  │         sha256(rawToken), 24시간)               │
  │       ※ 원문은 DB에 저장하지 않음                 │
  │                                                │
  │  5. publishEvent(SignupCompleted(email,        │
  │                  rawToken))                    │
  │       ※ 아직 발송되지 않음                       │
  │                                                │
  ├─[커밋]─────────────────────────────────────────┘
  │
  │  ← 201 { "id": ... }  (사용자에게 응답)
  │
  └─ @TransactionalEventListener(AFTER_COMMIT) + @Async
       │
       ▼
     SignupMailListener.onSignupCompleted
       │
       ├── Thymeleaf: mail/email-verification 렌더링
       │     actionUrl = {APP_BASE_URL}/verify-email?token={URL인코딩된 rawToken}
       │     expiresIn = "24시간"
       │
       ▼
     MailSenderPort.send(to, subject, htmlBody)
       ├── SmtpMailSender    (host + username 둘 다 있을 때)
       └── LoggingMailSender (그 외 — 본문을 로그로만)
       
       실패 → catch (RuntimeException) → log.error → 끝
              ※ 사용자는 이미 201을 받았음
```

### 이 설계가 만드는 두 가지 성질 `확인됨`

| 성질 | 좋은 점 | 나쁜 점 |
|---|---|---|
| 메일이 커밋 후 | SMTP 장애가 가입을 막지 않음 | 발송 실패를 아무도 모름 |
| 비동기 | 응답이 SMTP 지연에 묶이지 않음 | 예외가 호출자에게 전달되지 않음 |

`todo.md`의 "발송된 메일이 없음"이 조용히 발생하는 구조적 이유입니다.

## DF-003 이메일 인증

```
메일의 링크 클릭
  │  GET {APP_BASE_URL}/verify-email?token=xxx
  ▼
SpaResourceConfig → /verify-email 은 파일이 없음 → index.html
  ▼
React Router → VerifyEmailPage
  │  useEffect: 쿼리에서 token 추출
  ▼
GET /api/members/verify-email?token=xxx
  ▼
VerificationService.verifyEmail(rawToken)
  │
  ├── sha256(rawToken) 으로 조회 (purpose = EMAIL_VERIFICATION)
  │     없음 → 400 "유효하지 않은 이메일 인증 토큰입니다."
  │
  ├── isUsable("EMAIL_VERIFICATION")
  │     purpose 불일치 / used_at 있음 / 만료 → 400
  │
  ├── token.getMember().verifyEmail()
  │     status == PENDING 일 때만 ACTIVE 로 전환
  │
  └── token.markUsed()   used_at = now
  ▼
200 { "status": "verified" }
```

**두 번의 URL 진입**이 있습니다. 브라우저가 SPA 셸을 받고,
SPA가 다시 API를 호출합니다. `SpaResourceConfig`의 폴백이 첫 번째를 가능하게 합니다.

## DF-004 게시글 조회 — 조회수 증가 부수효과

```
GET /api/posts/{id}
  ▼
PostService.getPost  @Transactional  (readOnly 아님)
  │
  ├── postRepository.findById(id).filter(deletedAt == null)
  │     없음 → NoSuchElementException → 500  ※ 404여야 함
  │
  ├── post.getBoard().checkReadable(actor)
  │     회원제 + 비로그인 → 401
  │
  ├── 조회수 분기
  │   │
  │   ├─ 로그인 →  INSERT INTO post_view_log
  │   │            (post_id, member_id, viewed_on)
  │   │            VALUES (...) ON CONFLICT DO NOTHING
  │   │              반환 1 → increaseViewCount(id)   원자적 UPDATE
  │   │              반환 0 → 오늘 이미 봄, 증가 없음
  │   │
  │   └─ 비로그인 → increaseViewCount(id)   무조건
  │                 ※ PRD 2.6 의 쿠키 방식 미구현
  │
  ├── attachmentRepository.findByPostId(id)
  │
  └── PostResponse.from(post, memberId, isAdmin, attachments)
  ▼
200 PostResponse
```

### `isOwner` 계산의 특이점 `확인됨`

```java
if (isAdmin)                                            owner = true;
else if (회원 글 && 작성자 == 현재 사용자)                 owner = true;
else if (p.getMember() == null && currentMemberId == null) owner = true;
```

(`post/adapter/in/web/dto/PostResponse.java:24-31`)

세 번째 조건은 **비로그인 조회자에게 모든 비회원 글이 `isOwner: true`로 보인다**는 뜻입니다.
누가 썼든 상관없습니다.

실제 권한은 `Post.checkEditable`의 비밀번호 검증이 막으므로 보안 결함은 아닙니다.
하지만 UI가 모든 비회원 글에 수정·삭제 버튼을 띄우게 됩니다 — 눌러야 비밀번호가 틀렸음을 압니다. `미결정`

### `authorName`이 로그인 아이디 `확인됨`

```java
.authorName(p.getMember() != null ? p.getMember().getUsername() : p.getGuestNickname())
```

(`PostResponse.java:38`)

회원 글의 작성자 표시가 `username`(**로그인 아이디**)입니다.
`name`도 `nickname`도 아닙니다.

`V3`에서 닉네임 컬럼을 추가한 목적과 어긋나고,
**모든 방문자에게 회원의 로그인 아이디가 공개**됩니다. `미결정`
→ [../../security/privacy.md](../../security/privacy.md)

## DF-005 파일 업로드 → 게시글 첨부

두 단계로 나뉩니다.

```
[1단계] 파일 업로드
POST /api/files  (multipart, permitAll — 비로그인도 가능)
  ▼
FileStorageService.storeFile
  ├── storedName = UUID + 원본 확장자
  ├── file.transferTo(rootPath/storedName)   디스크에 씀
  └── contentType = file.getContentType()    ※ 클라이언트가 보낸 값 그대로
      mediaKind   = MediaKind.from(contentType)
  ▼
200 FileResponse { originalName, storedName, contentType, mediaKind, byteSize }
      ※ 이 시점에 attachment 행은 없음 — 글을 저장해야 생김

[2단계] 게시글 작성
POST /api/boards/{slug}/posts
  body.attachments = [1단계의 FileResponse, ...]
  ▼
PostService.createPost
  ├── board.checkWritable(actor)
  ├── Post 저장
  └── for (FileResponse f : cmd.getAttachments())
          attachmentRepository.save(Attachment.of(post, ...))
      ※ board.allowsAttachment 를 검사하지 않음
```

### 이 분리가 만드는 문제 `미결정`

| 문제 | 설명 |
|---|---|
| 고아 파일 | 1단계만 하고 2단계를 안 하면 디스크에 파일만 남음. 정리 없음 |
| 정책 우회 | 첨부 불가 게시판에도 첨부가 붙음 |
| 무인증 업로드 | `permitAll`이라 누구나 100MB까지 업로드 |
| 값 위조 | 클라이언트가 `FileResponse`를 조작해 보낼 수 있음 (`originalName`, `contentType`, `byteSize`) |

마지막 항목: 2단계에서 `attachments` 배열의 값을 **검증 없이 그대로 저장**합니다.
실제 파일과 다른 메타데이터가 DB에 들어갈 수 있습니다.

## DF-006 파일 다운로드

```
GET /api/files/{storedName}[?download=1]
  ▼
FileStorageService.loadFileAsResource
  ├── filePath = rootPath.resolve(storedName).normalize()
  ├── if (!filePath.startsWith(rootPath)) → SecurityException → 500
  └── 존재·읽기 가능 확인 → 아니면 RuntimeException → 500
  ▼
FileController
  ├── contentType = Files.probeContentType(path)   ※ 서버가 재판정
  └── Content-Disposition
        ?download 있음 → attachment
        없음           → inline
  ▼
200 파일 스트림
```

**인증이 없습니다.** `storedName`(UUID)만 알면 회원제 게시판의 첨부도 받을 수 있습니다.
UUID가 사실상의 접근 토큰 역할을 합니다.
→ [../../security/threat-model.md](../../security/threat-model.md) T-003

다운로드 시 `Content-Type`을 다시 판정하는 것은 좋은 선택입니다 —
업로드 시 저장된(신뢰할 수 없는) 값을 쓰지 않습니다.

## DF-007 로그인과 세션

```
POST /api/auth/login  (form 인코딩, CSRF 면제)
  ▼
UsernamePasswordAuthenticationFilter
  ▼
CustomUserDetailsService.loadUserByUsername
  ├── memberRepository.findByUsername
  │     없음 → UsernameNotFoundException
  └── new CustomUserDetails(member)
  ▼
계정 상태 검사
  ├── isAccountNonLocked()  status != SUSPENDED
  └── isEnabled()           status == ACTIVE || status == PENDING
  ▼
PasswordEncoder.matches
  ▼
세션 고정 방어: changeSessionId()
동시 세션: maximumSessions(1) → 기존 세션 만료
  ▼
JsonAuthenticationSuccessHandler → 200
Set-Cookie: JSESSIONID=...
Set-Cookie: XSRF-TOKEN=...
  ▼
[클라이언트] GET /api/me → authStore.setUser()
```

## DF-008 세션 만료 감지

```
아무 API 호출 → 401
  ▼
axios 응답 인터셉터  (lib/axios.ts:26-34)
  ├── isUnauthorized(error)  status === 401
  └── useAuthStore.getState().logout()
        { user: null, isAuthenticated: false, isAdmin: false }
  ▼
구독 중인 컴포넌트가 리렌더 → 비로그인 UI
```

라우트 가드가 없는 이유입니다. **서버 응답이 단일 진실 원천**입니다.

## 관련 문서

- [components.md](components.md) — 각 단계의 클래스
- [../../features/api-behavior.md](../../features/api-behavior.md) — 엔드포인트별 동작
- [../../features/error-policy.md](../../features/error-policy.md) — 오류 경로
- [../../security/threat-model.md](../../security/threat-model.md) — 흐름별 위협
