# 위협 모델

> 상태: 각 위협의 **현재 방어 수준은 `확인됨`**(코드 확인), 위험도 평가와 대응안은 `제안`입니다.
> 방법론: STRIDE + 데이터 흐름 기반.

## 신뢰 경계 `확인됨`

```
[인터넷] ─────────────────────────────────────────────
   │  신뢰하지 않음
   │  ─ 모든 요청 파라미터·본문
   │  ─ 업로드 파일과 그 Content-Type
   │  ─ 클라이언트의 CAPTCHA 통과 주장
   ▼
[Spring Security 필터]  세션·CSRF·경로 규칙
   ▼
[컨트롤러]  @Valid 형식 검증
   ▼
[도메인]  권한 판정 ← 실질적 방어선
   ▼
[DB 제약]  UNIQUE·CHECK ← 최종 방어선
   
[내부] ────────────────────────────────────────────────
   PostgreSQL, SMTP 제공자, CAPTCHA 제공자 (신뢰함)
```

## 위험도 기준 `제안`

| 등급 | 뜻 |
|---|---|
| **높음** | 계정 탈취, 데이터 유출, 서비스 중단으로 직결 |
| 중간 | 조건이 갖춰지면 피해 발생 |
| 낮음 | 영향이 제한적이거나 악용이 어려움 |

## T-001 세션 탈취 (Information Disclosure) — **높음**

### 시나리오

같은 네트워크의 공격자가 평문 HTTP 트래픽에서 `JSESSIONID`를 가로챕니다.
쿠키를 그대로 재사용하면 피해자로 로그인된 상태가 됩니다.

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| HTTPS 강제 | **없음** — `requiresChannel()` 없음, `server.ssl.*` 없음 |
| 쿠키 `Secure` | **없음** |
| 쿠키 `HttpOnly` | 있음 (서블릿 기본값) |
| 쿠키 `SameSite` | **없음** |
| 세션 고정 방어 | 있음 (`changeSessionId()`) |
| 동시 세션 1개 | 있음 — 탈취 시 오히려 원 사용자가 쫓겨남 |

### 평가

**가장 위험한 위협입니다.** 애플리케이션 계층 방어가 아무리 좋아도
전송이 평문이면 무의미합니다.

리버스 프록시에서 TLS를 종단하는 구성을 전제한 것으로 보이나
저장소에 그 구성이 없습니다. 그리고 프록시가 있어도
쿠키에 `Secure`가 없으면 평문 요청에 쿠키가 실립니다.

### 대응 `제안`

1. 리버스 프록시 TLS 종단 + HTTP → HTTPS 리다이렉트
2. 쿠키 `secure: true`, `same-site: lax`
3. HSTS 헤더

## T-002 무인증 파일 업로드 (Denial of Service) — **높음**

### 시나리오

```
POST /api/files
Content-Type: multipart/form-data
(100MB 파일)
```

인증 없이 반복 호출해 디스크를 채웁니다.
디스크가 차면 DB 쓰기·로그 기록까지 실패해 서비스가 멈춥니다.

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| 인증 | **없음** — `SecurityConfig.java:35`가 `permitAll` |
| 도메인 권한 판정 | **없음** — `FileController`·`FileStorageService`에 검사 없음 |
| 파일당 크기 제한 | 있음 (100MB) |
| 총량 제한 | **없음** |
| 속도 제한 | **없음** |
| 고아 파일 정리 | **없음** |

### 왜 생겼는가 `확인됨`

`SecurityConfig`가 대부분 `permitAll`이고 실제 판정을 도메인에 맡기는 구조인데,
**파일 도메인에는 그 판정이 없습니다.**
게시글은 `Board.checkWritable`이, 댓글은 `CommentService`가 막는데 파일만 비어 있습니다.

구조의 약점이 실제로 드러난 사례입니다.

### 대응 `제안`

1. `POST /api/files`를 `authenticated()`로 (비회원 첨부를 허용할 게시판이 없다면)
2. 또는 게시판 정책 검사 추가
3. 고아 파일 정리 배치 (24시간)
4. 사용자별 업로드 총량·속도 제한

## T-003 첨부파일 무단 열람 (Information Disclosure) — 중간

### 시나리오

```
GET /api/files/{UUID}
```

인증 없이 누구나 받을 수 있습니다.
회원제 게시판(`archive`, 자료실)의 첨부도 마찬가지입니다.

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| 인증 | **없음** |
| 게시판 권한 확인 | **없음** |
| 파일명 추측 방지 | 있음 — UUID (`FileStorageService.java:50`) |

### 평가

UUID는 추측하기 어려우므로(122비트 랜덤) **무차별 탐색은 비현실적**입니다.
사실상 capability URL 방식입니다.

문제는 링크가 유출됐을 때입니다 — Referer 헤더, 브라우저 히스토리,
공유된 URL을 통해 새어나가면 **되돌릴 방법이 없습니다.**
파일명을 바꾸는 기능도, 접근을 취소하는 기능도 없습니다.

자료실이 회원제(`requires_auth_to_read = true`)인데
그 첨부는 비회원도 받을 수 있다는 것이 정책 모순입니다.

### 대응 `제안`

다운로드 시 `attachment` → `post` → `board.checkReadable(actor)` 순으로 확인.
현재 `FileStorageService`는 `attachment` 테이블을 보지도 않고
파일시스템에서 바로 읽으므로 구조 변경이 필요합니다.

## T-004 비회원 글 비밀번호 노출 (Information Disclosure) — 중간

### 시나리오

```
DELETE /api/posts/123?guestPassword=mysecret
```

비밀번호가 **쿼리 파라미터**로 전달됩니다.

| 남는 곳 | 설명 |
|---|---|
| 웹서버 접근 로그 | 기본적으로 쿼리 문자열 포함 |
| 프록시·로드밸런서 로그 | 동일 |
| 브라우저 히스토리 | 동일 |
| Referer 헤더 | 외부 링크 클릭 시 전달 가능 |

### 현재 방어 `확인됨`

`PUT /api/posts/{id}`는 본문에 담는데 `DELETE`만 쿼리입니다
(`post/adapter/in/web/PostController.java:61`).

비회원 비밀번호는 bcrypt로 해시되어 저장되지만,
**전송·로깅 과정에서는 평문**입니다.

### 평가

비회원 글 비밀번호는 계정 비밀번호보다 민감도가 낮습니다.
다만 사용자가 계정 비밀번호를 재사용할 가능성이 있고,
길이 제약이 없어(`PostCommand.guestPassword`) 짧은 값이 쓰일 수 있습니다.

### 대응 `제안`

`POST /api/posts/{id}/delete`로 바꿔 본문에 담거나, 헤더로 전달.

## T-005 경로 탈출 (Tampering) — 낮음

### 시나리오

```
GET /api/files/../../../etc/passwd
```

### 현재 방어 `확인됨`

```java
Path filePath = rootPath.resolve(storedName).normalize();
if (!filePath.startsWith(rootPath)) {
    throw new SecurityException("Path traversal attempt");
}
```

(`attachment/application/FileStorageService.java:70-73`)

`normalize()` 후 `startsWith` 검사는 **올바른 방어**입니다.

### 남은 문제 `확인됨`

`SecurityException`에 핸들러가 없어 **500 + 스택트레이스**가 됩니다.

| 영향 | 내용 |
|---|---|
| 공격 시도가 정상 오류율에 섞임 | 탐지 어려움 |
| 로그가 따로 남지 않음 | 추적 불가 |
| 스택트레이스 노출 가능 | 내부 구조 힌트 |

업로드 경로(`storeFile`)에는 이 검사가 없지만,
저장 파일명이 UUID로 생성되므로 사용자 입력이 경로에 들어가지 않습니다.
원본 확장자만 사용자 값에서 오는데, `lastIndexOf(".")` 이후 부분이라
`../`를 넣어도 확장자 자리에만 붙습니다. **현재는 안전합니다.**

### 대응 `제안`

`SecurityException` 핸들러를 추가해 403 + `WARN` 로그.

## T-006 업로드 파일 위장 (Spoofing) — 중간

### 시나리오

```
POST /api/files
Content-Type: image/png
(실제 내용은 HTML + <script>)
```

`media_kind = IMAGE`로 저장됩니다.

### 현재 방어 `확인됨`

| 지점 | 처리 |
|---|---|
| 업로드 | `file.getContentType()` — **클라이언트 제공 값 그대로** (`FileStorageService.java:58-66`) |
| DB 저장 | 위 값과 그로부터 계산된 `media_kind` |
| 다운로드 | `Files.probeContentType` — **서버 재판정** (`FileController.java:55-61`) |
| SVG | `IMAGE`가 아니라 `FILE`로 강등 (`MediaKind.java:7`) |

### 평가

**다운로드 시 재판정이 실질적 방어**입니다.
브라우저에 내려가는 `Content-Type`은 서버가 정하므로
`image/png`로 위장한 HTML이 HTML로 실행되지는 않습니다.

다만:

| 문제 | 내용 |
|---|---|
| DB의 `media_kind`가 거짓 | 프론트엔드가 이 값으로 렌더링 방식을 정함 |
| `.html` 파일 자체는 허용 | `probeContentType`이 `text/html`을 반환하면 실행됨 |
| 확장자 화이트리스트 없음 | 어떤 파일이든 업로드 가능 |
| `nosniff` 헤더 없음 | 브라우저 추론 여지 |

`PRD.md` 6.3의 업로드 보안 체크리스트(결정 D4)가 내용 기반 판정을 요구하는데
이행되지 않았습니다.

### 대응 `제안`

1. 업로드 시 `Files.probeContentType` 또는 Apache Tika로 내용 판정
   (Tika는 이미 의존성에 있습니다 — `spring-ai-tika-document-reader`)
2. 허용 확장자 화이트리스트
3. 다운로드 응답에 `X-Content-Type-Options: nosniff`
4. HTML·JS 등 실행 가능 형식은 항상 `attachment`로

## T-007 자동화 가입·스팸 (Spoofing) — **높음**

### 시나리오

`CAPTCHA_MODE=fake`인 상태에서 토큰 `dev-captcha`를 넣어 무제한 가입합니다.

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| CAPTCHA 서버 검증 | 구조는 있음 (`SignupService.java:30`) |
| 운영 provider | **미확정** (`plan.md` 선결 결정 #1) |
| 기본 모드 | `fake` — 고정 문자열만 통과 |
| 속도 제한 | 없음 |
| CAPTCHA 실패 로그 | **없음** |

### 평가

CAPTCHA를 서버에서 재검증하는 구조 자체는 올바릅니다
(클라이언트 주장을 믿지 않음).

문제는 **기본값이 `fake`**라는 것입니다. 배포 시 설정을 바꾸지 않으면
방어가 전혀 없는 상태로 노출됩니다. 그리고 그것을 경고하는 코드도 없습니다.

실패 로그가 없어 **공격받고 있는지조차 알 수 없습니다.**

### 대응 `제안`

1. 운영 provider 확정 및 `mode=remote` 설정
2. `mode=fake`로 기동 시 `WARN` 로그
3. CAPTCHA 실패 시 로그 기록
4. 가입 속도 제한

**provider 연동 시 주의**: 현재 구현은 JSON 본문으로 요청합니다.
reCAPTCHA·hCaptcha는 form 인코딩을 요구하므로 어댑터 수정이 필요할 수 있습니다.
→ [../technology/api/integration-contracts.md](../technology/api/integration-contracts.md)

## T-008 무차별 대입 로그인 (Spoofing) — **높음**

### 시나리오

```
POST /api/auth/login  (username=hong&password=...)  반복
```

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| 시도 횟수 제한 | **없음** |
| 계정 잠금 | **없음** |
| CAPTCHA | **없음** |
| 속도 제한 | **없음** |
| 실패 로그 | **없음** |
| CSRF | **면제 경로** |
| 비밀번호 복잡도 | **없음** (`aaaaaaaa` 가능) |

### 평가

**방어가 하나도 없습니다.**

게다가 `GET /api/members/username-availability`가
유효한 아이디를 알려주므로 공격 대상을 추릴 수 있습니다.
(이 API 자체는 의도된 설계이지만, 시도 제한이 없는 상태에서는 위험을 키웁니다.)

`maximumSessions(1)`이 있어 공격자가 로그인에 성공하면
원 사용자가 쫓겨나므로 **피해가 즉시 드러난다**는 점은 역설적 이점입니다.

### 대응 `제안`

`AuthenticationFailureBadCredentialsEvent`를 받아 IP·계정별 실패를 세고 임계값 초과 시 차단.

## T-009 메일 폭탄 (Denial of Service) — 중간

### 시나리오

```
POST /api/members/verify-email/resend  {"email":"victim@example.com"}
```

반복 호출로 피해자에게 메일을 쏟아붓습니다.

### 현재 방어 `확인됨`

| 방어 | 상태 |
|---|---|
| 횟수 제한 | **없음** |
| CAPTCHA | **없음** |
| 기존 토큰 무효화 | **없음** — 유효 토큰이 계속 쌓임 |

`PENDING` 계정에만 실제로 발송되므로 대상이 제한적이지만,
`verification_token` 행이 무한히 쌓이는 부작용도 있습니다.

비밀번호 재설정 요청에는 CAPTCHA가 있어 상대적으로 안전합니다.

### 대응 `제안`

재발송에 쿨다운(예: 60초)과 일일 횟수 제한. 새 토큰 발급 시 기존 토큰 무효화.

## T-010 개인정보 노출 — 중간

### 시나리오

게시글·댓글 목록에 **회원의 로그인 아이디가 그대로 표시**됩니다.

```java
.authorName(p.getMember() != null ? p.getMember().getUsername() : p.getGuestNickname())
```

(`post/adapter/in/web/dto/PostResponse.java:38`,
`PostListItemResponse.java:19`, `CommentResponse.java:23`)

### 평가 `확인됨`

`username`은 **로그인 아이디**입니다. `name`도 `nickname`도 아닙니다.

| 영향 | 내용 |
|---|---|
| 아이디 수집 | 자유게시판은 비회원도 볼 수 있어 크롤링 가능 |
| T-008과 결합 | 무차별 대입의 대상 목록이 됨 |
| `V3` 의도와 배치 | 닉네임 컬럼을 추가한 목적이 표시명인데 쓰이지 않음 |

### 대응 `제안`

`authorName`을 `nickname ?? name`으로 바꿉니다.
`MeResponse`에 `nickname`을 추가하는 것도 함께 필요합니다(현재 빠져 있음).

## T-011 오류 응답을 통한 정보 노출 — 낮음

### 현재 상태 `확인됨`

| 경로 | 노출 |
|---|---|
| `IllegalArgumentException` | 예외 메시지 그대로 응답에 실림 |
| 미처리 예외 | Spring 기본 500 응답 |
| `show-sql: true` | SQL과 파라미터가 로그에 |
| `MAIL_DEBUG=true` | SMTP 인증 정보가 로그에 |

현재 `IllegalArgumentException`으로 나가는 메시지는 모두 의도된 문구입니다.
다만 앞으로 내부 정보를 담은 예외가 추가되면 그대로 새어나갑니다.

### 대응 `제안`

미처리 예외에 대한 핸들러를 추가해 일반화된 메시지만 반환.
`show-sql`을 환경 변수로 분리.

## T-012 인증 우회 — 낮음(설계상) `미결정`

### 관찰

이메일 인증(`PENDING` → `ACTIVE`)이 **실질적으로 아무것도 막지 않습니다.**

- `CustomUserDetails.isEnabled()`가 `PENDING`을 허용
- `Board.checkReadable`이 `status`를 보지 않음

따라서 인증하지 않아도 회원제 게시판을 이용할 수 있습니다.

### 평가

의도적 설계일 수 있습니다 — 메일이 안 갈 때 사용자가 완전히 막히지 않게 하는 완충.
그러나 문서화되지 않아 **의도인지 누락인지 불명확**합니다.

이메일 소유 확인이 실질적 관문이 아니므로, 아무 이메일로나 가입해
회원 기능을 쓸 수 있습니다(T-007과 결합하면 스팸 계정 양산).

### 대응 `미결정`

결정이 필요합니다. 막으려면 `isEnabled()`에서 `PENDING`을 빼거나
`Board.checkReadable`이 `status`를 보게 해야 합니다.
**현재 메일 발송 문제가 해결된 뒤에** 바꾸는 것이 안전합니다.

## 위협 요약 `제안`

| ID | 위협 | 위험도 | 방어 |
|---|---|---|---|
| T-001 | 세션 탈취 (평문 전송) | **높음** | 거의 없음 |
| T-002 | 무인증 업로드 (DoS) | **높음** | 크기 제한만 |
| T-007 | 자동화 가입 (`fake` CAPTCHA) | **높음** | 설정에 의존 |
| T-008 | 무차별 대입 로그인 | **높음** | 없음 |
| T-003 | 첨부 무단 열람 | 중간 | UUID 추측 난이도 |
| T-004 | 비밀번호 쿼리 노출 | 중간 | 없음 |
| T-006 | 파일 위장 | 중간 | 다운로드 재판정 |
| T-009 | 메일 폭탄 | 중간 | 없음 |
| T-010 | 로그인 아이디 노출 | 중간 | 없음 |
| T-005 | 경로 탈출 | 낮음 | **적절히 방어됨** |
| T-011 | 오류 정보 노출 | 낮음 | 부분 |
| T-012 | 인증 우회 | 낮음 | 설계 결정 필요 |

## 잘 방어된 영역 `확인됨`

공정하게 기록합니다.

| 영역 | 근거 |
|---|---|
| CSRF | `csrf().spa()` + 회귀 테스트 3개 |
| SQL 인젝션 | JPA 파라미터 바인딩 일관 사용 |
| 경로 탈출 | `normalize()` + `startsWith` |
| 비밀번호 저장 | bcrypt (`DelegatingPasswordEncoder`) |
| 토큰 저장 | SHA-256 해시, 원문 미저장 |
| 토큰 수명 | 1회용 + 만료 |
| 계정 열거 | 메일 API 3종 202 고정 |
| 동시성 | 복합 PK + `ON CONFLICT` |
| SVG XSS | `FILE`로 강등 |

## 관련 문서

- [security-requirements.md](security-requirements.md) — 요구사항 대응표
- [security-architecture.md](security-architecture.md) — 방어 계층
- [security-test-plan.md](security-test-plan.md) — 검증 방법
- [incident-response.md](incident-response.md) — 사고 대응
