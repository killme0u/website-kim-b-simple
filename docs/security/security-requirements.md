# 보안 요구사항

> 상태: 각 요구사항의 충족 여부는 `확인됨`(코드 확인). 미충족 항목의 대응안은 `제안`입니다.

## 읽는 법

| 표기 | 뜻 |
|---|---|
| 충족 | 코드에 구현되어 있음 |
| 부분 | 일부만 구현 |
| **미충족** | 구현 없음 |

## SR-001 인증

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-001-1 | 비밀번호를 단방향 해시로 저장한다 | 충족 | `DelegatingPasswordEncoder` (bcrypt), `config/SecurityConfig.java:70-73` |
| SR-001-2 | 세션 고정 공격을 방어한다 | 충족 | `changeSessionId()`, `SecurityConfig.java:62` |
| SR-001-3 | 계정당 동시 세션을 제한한다 | 충족 | `maximumSessions(1)`, `:63` |
| SR-001-4 | 정지·탈퇴 계정의 로그인을 차단한다 | 충족 | `CustomUserDetails.java:47-49, 57-59` |
| SR-001-5 | 로그인 시도 횟수를 제한한다 | **미충족** | 구현 없음 |
| SR-001-6 | 로그인에 CAPTCHA를 적용한다 | **미충족** | 가입·재설정에만 있음 |
| SR-001-7 | 비밀번호 최소 길이를 강제한다 | 충족 | 8자 (`@Size(min=8)`) |
| SR-001-8 | 비밀번호 복잡도를 강제한다 | **미충족** | `aaaaaaaa` 통과 |
| SR-001-9 | 비밀번호 변경 시 기존 세션을 무효화한다 | **미충족** | `VerificationService.changePassword`가 세션을 건드리지 않음 |
| SR-001-10 | 다중 요소 인증 | **미충족** | 범위 밖 |

### SR-001-5가 가장 시급합니다 `미결정`

로그인 시도 제한이 없어 **무차별 대입에 무방비**입니다.

- `POST /api/auth/login`은 CSRF 면제
- CAPTCHA 없음
- 시도 횟수 제한 없음
- 계정 잠금 없음
- **실패 로그조차 남지 않음**

아이디 중복 확인 API가 유효한 아이디 목록을 알려주므로
공격자가 대상을 추릴 수도 있습니다.

대응 제안 `제안`: Spring Security의 `AuthenticationFailureBadCredentialsEvent`를 받아
IP·계정별 실패를 세고, 임계값 초과 시 일시 차단.

## SR-002 전송 보안

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-002-1 | 모든 통신을 HTTPS로 한다 | **미충족** | `requiresChannel()` 없음, `server.ssl.*` 없음 |
| SR-002-2 | 세션 쿠키에 `Secure` 속성을 설정한다 | **미충족** | 설정 없음 |
| SR-002-3 | 세션 쿠키에 `HttpOnly`를 설정한다 | 충족 | 서블릿 기본값 |
| SR-002-4 | 쿠키에 `SameSite`를 설정한다 | **미충족** | 설정 없음 |
| SR-002-5 | HSTS 헤더를 보낸다 | **미충족** | 설정 없음 |

### 전송 보안 전체가 비어 있습니다 `확인됨`

`docker-compose.yml`이 8080을 평문으로 노출합니다.
리버스 프록시에서 TLS를 종단하는 구성을 전제한 것으로 보이나,
**그 구성이 저장소 어디에도 없습니다.**

프록시가 있어도 쿠키에 `Secure`가 없으면 평문 요청에도 쿠키가 실립니다.

대응 제안 `제안`:

```yaml
server:
  servlet:
    session:
      cookie:
        secure: ${COOKIE_SECURE:false}
        same-site: lax
```

`XSRF-TOKEN` 쿠키는 JS가 읽어야 하므로 `httpOnly`는 `false`를 유지해야 합니다
(`csrf().spa()`의 전제).

## SR-003 인가

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-003-1 | 회원제 게시판은 비로그인 접근을 차단한다 | 충족 | `Board.checkReadable` |
| SR-003-2 | 글 수정·삭제는 작성자·관리자만 가능하다 | 충족 | `Post.checkEditable` |
| SR-003-3 | 댓글 수정·삭제는 작성자·관리자만 가능하다 | 충족 | `CommentService.java:64-66, 76-78` |
| SR-003-4 | 비회원 글은 비밀번호로만 수정·삭제한다 | 충족 | `Post.java:85-87` |
| SR-003-5 | 파일 업로드에 인증을 요구한다 | **미충족** | `permitAll` + 도메인 판정 없음 |
| SR-003-6 | 파일 다운로드에 권한을 검사한다 | **미충족** | UUID만 알면 누구나 |
| SR-003-7 | 게시판 첨부 정책을 서버에서 강제한다 | **미충족** | `allowsAttachment` 미검증 |
| SR-003-8 | 관리자 기능에 별도 인가를 둔다 | 부분 | 도메인에서 `ADMIN` 확인. 관리 화면 없음 |

### SR-003-5·6이 함께 문제입니다 `확인됨`

```java
.requestMatchers(HttpMethod.POST, "/api/boards/*/posts", "/api/files").permitAll()
GET "/api/files/**" permitAll
```

| 결과 | 내용 |
|---|---|
| 무인증 업로드 | 누구나 100MB × 무제한 |
| 무인증 다운로드 | `storedName`(UUID)만 알면 회원제 게시판 첨부도 열람 |

UUID가 사실상의 접근 토큰(capability URL) 역할을 합니다.
추측은 어렵지만, 링크가 유출되면 되돌릴 수 없습니다.

## SR-004 입력 검증

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-004-1 | 요청 DTO를 서버에서 검증한다 | 충족 | `@Valid` + Bean Validation |
| SR-004-2 | SQL 인젝션을 방어한다 | 충족 | JPA 파라미터 바인딩, 네이티브 쿼리도 `@Param` |
| SR-004-3 | 경로 탈출을 차단한다 | 충족 | `FileStorageService.java:71-73` |
| SR-004-4 | 업로드 파일 크기를 제한한다 | 충족 | 100MB |
| SR-004-5 | 업로드 파일 형식을 검증한다 | **미충족** | 클라이언트 `Content-Type` 신뢰 |
| SR-004-6 | 허용 확장자 화이트리스트를 둔다 | **미충족** | 없음 |
| SR-004-7 | 비회원 글 비밀번호에 최소 길이를 둔다 | **미충족** | `PostCommand.guestPassword`에 제약 없음 |

### SR-004-2 보충 `확인됨`

네이티브 쿼리가 하나 있지만 안전합니다.

```java
@Query(value = """
    INSERT INTO post_view_log (post_id, member_id, viewed_on)
    VALUES (:postId, :memberId, :viewedOn)
    ON CONFLICT DO NOTHING
    """, nativeQuery = true)
```

문자열 연결이 아니라 `@Param` 바인딩이라 인젝션 위험이 없습니다.

키워드 검색도 JPQL 파라미터 바인딩입니다.

## SR-005 XSS 방어

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-005-1 | SVG를 이미지로 인라인 렌더링하지 않는다 | 충족 | `MediaKind.from` (`attachment/domain/MediaKind.java:7`) |
| SR-005-2 | 마크다운 렌더링 시 스크립트를 제거한다 | 부분 | `react-markdown` 기본값이 HTML 미허용 |
| SR-005-3 | CSP 헤더를 설정한다 | **미충족** | 설정 없음 |
| SR-005-4 | 업로드 파일을 별도 도메인에서 서빙한다 | **미충족** | 같은 오리진 |

### SR-005-2 보충 `제안`

`react-markdown`은 기본적으로 원시 HTML을 렌더링하지 않습니다.
`rehype-raw` 같은 플러그인을 추가하지 않는 한 안전합니다.

`frontend-react/package.json`에 그런 플러그인이 없어 현재는 안전합니다.
**앞으로 추가하지 않는 것이 중요합니다.**

### SR-005-4가 중요한 이유 `미결정`

업로드 파일이 애플리케이션과 같은 오리진(`/api/files/...`)에서 서빙됩니다.
HTML 파일을 올리고 `inline`으로 열면 같은 오리진에서 스크립트가 실행되어
세션 쿠키에 접근할 수 있습니다.

완화 요소:
- `Content-Type`을 서버가 재판정 (`Files.probeContentType`)
- SVG는 `FILE`로 강등

그러나 `.html` 파일 자체는 막히지 않습니다.
`probeContentType`이 `text/html`을 반환하면 브라우저가 실행합니다.

대응 제안 `제안`: 다운로드 응답에
`Content-Security-Policy: default-src 'none'`과
`X-Content-Type-Options: nosniff`를 추가하거나,
모든 첨부를 `attachment`로만 내려보냅니다.

## SR-006 CSRF

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-006-1 | 상태 변경 요청에 CSRF 토큰을 요구한다 | 충족 | `csrf().spa()` |
| SR-006-2 | 회귀 테스트로 고정한다 | 충족 | `SecurityConfigTest` 3개 |
| SR-006-3 | 면제 경로를 최소화한다 | 충족 | 로그인·가입 2개만 |

CSRF는 이 시스템에서 **가장 잘 갖춰진 영역**입니다.
실제 사고(로그아웃 403)를 겪고 회귀 테스트까지 남겼습니다.

## SR-007 비밀값 관리

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-007-1 | 비밀값을 저장소에 커밋하지 않는다 | 부분 | `.env`는 제외되나 DB 비밀번호는 커밋됨 |
| SR-007-2 | 양식 파일에 실제 값을 넣지 않는다 | 충족 | `.env.example` 상단 경고 |
| SR-007-3 | 토큰 원문을 저장하지 않는다 | 충족 | SHA-256 해시만 |
| SR-007-4 | 비밀값을 로그에 남기지 않는다 | 부분 | `MAIL_DEBUG=true`면 노출 |
| SR-007-5 | 이미지에 비밀값을 포함하지 않는다 | 충족 | `Dockerfile`이 `.env`를 COPY하지 않음 |

### SR-007-1 위반 `확인됨`

```yaml
# application.yml
username: board_user
password: board_password
```

```yaml
# docker-compose.yml
- POSTGRES_PASSWORD=board_password
```

**DB 자격 증명이 평문으로 커밋되어 있습니다.**
SMTP·CAPTCHA는 `.env`로 뺐는데 DB만 남았습니다.

→ [secrets-management.md](secrets-management.md)

## SR-008 계정 열거 방지

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| SR-008-1 | 비밀번호 재설정이 계정 유무를 노출하지 않는다 | 충족 | 202 고정 |
| SR-008-2 | 인증 메일 재발송이 노출하지 않는다 | 충족 | 202 고정 |
| SR-008-3 | 아이디 찾기가 노출하지 않는다 | 충족 | 202 고정 |
| SR-008-4 | 로그인 실패 메시지가 노출하지 않는다 | 미확인 | `JsonAuthenticationFailureHandler` 내용 미확인 |

**의도된 예외**: 아이디·닉네임 중복 확인 API는 존재 여부를 그대로 알려줍니다.
가입 UX를 위한 것이며 `PRD.md` 2.4가 이 트레이드오프를 명시했습니다.

## SR-009 감사·추적

| ID | 요구사항 | 상태 |
|---|---|---|
| SR-009-1 | 로그인 성공·실패를 기록한다 | **미충족** |
| SR-009-2 | 권한 거부를 기록한다 | **미충족** |
| SR-009-3 | 관리자 행위를 기록한다 | **미충족** |
| SR-009-4 | CAPTCHA 실패를 기록한다 | **미충족** |
| SR-009-5 | 메일 발송 결과를 기록한다 | **미충족** |

**감사 로그가 전혀 없습니다.** 사고가 나면 무엇이 일어났는지 재구성할 수 없습니다.

## SR-010 가용성 보호

| ID | 요구사항 | 상태 |
|---|---|---|
| SR-010-1 | 요청 속도를 제한한다 | **미충족** |
| SR-010-2 | 업로드 총량을 제한한다 | **미충족** |
| SR-010-3 | 인증 메일 재발송 횟수를 제한한다 | **미충족** |
| SR-010-4 | 페이지 크기 상한을 둔다 | **미충족** |

## 미충족 요약과 우선순위 `제안`

| 순위 | 요구사항 | 이유 |
|---|---|---|
| 1 | SR-002 전송 보안 (HTTPS + 쿠키 속성) | 세션 탈취 = 계정 탈취 |
| 2 | 운영 CAPTCHA (`fake` 모드 탈피) | 자동화 방어가 0 |
| 3 | SR-003-5 업로드 인증 | 디스크 고갈 + 저장소 오용 |
| 4 | SR-001-5 로그인 시도 제한 | 무차별 대입 무방비 |
| 5 | SR-005-4 첨부 서빙 격리 | 저장형 XSS |
| 6 | SR-009 감사 로그 | 사고 대응 불가 |
| 7 | SR-007-1 DB 자격 증명 분리 | 기본값 노출 |
| 8 | SR-010-3 재발송 제한 | 메일 폭탄 |

1~4는 운영 노출 전에 반드시 해결해야 합니다.

## 관련 문서

- [threat-model.md](threat-model.md) — 위협별 상세
- [authentication-authorization.md](authentication-authorization.md)
- [secure-coding-standard.md](secure-coding-standard.md)
- [security-test-plan.md](security-test-plan.md)
