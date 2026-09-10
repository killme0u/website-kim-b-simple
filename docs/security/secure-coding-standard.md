# 시큐어 코딩 기준

> 상태: 규칙은 `제안`이지만, **각 규칙마다 이 저장소의 준수·위반 사례를 인용**했습니다.
> 사례는 `확인됨`입니다.

## SC-001 입력을 신뢰하지 않는다

### 준수 사례 `확인됨`

**CAPTCHA를 서버에서 재검증합니다.** 클라이언트가 "통과했다"고 주장해도 믿지 않습니다.

```java
if (!captchaVerifier.verify(cmd.getCaptchaToken(), remoteAddress)) {
    throw new IllegalArgumentException("CAPTCHA 검증에 실패했습니다.");
}
```

(`member/application/SignupService.java:30-32`)

`plan.md`에 원칙으로 명시되어 있습니다 —
"클라이언트의 CAPTCHA 체크 상태를 신뢰하지 않고 서버 검증 결과만 사용한다".

### 위반 사례 `확인됨`

**업로드 파일의 `Content-Type`을 그대로 믿습니다.**

```java
String contentType = file.getContentType();   // 클라이언트가 보낸 값
if (contentType == null) contentType = "application/octet-stream";
return FileResponse.builder()
        .contentType(contentType)
        .mediaKind(MediaKind.from(contentType))
        ...
```

(`attachment/application/FileStorageService.java:58-66`)

**게시글 작성 시 `attachments` 배열도 검증하지 않습니다.**

```java
for (FileResponse f : cmd.getAttachments()) {
    attachmentRepository.save(Attachment.of(post, f.getOriginalName(),
        f.getStoredName(), f.getContentType(), f.getMediaKind(), f.getByteSize()));
}
```

(`post/application/PostService.java:85-89`)

클라이언트가 `byteSize`나 `mediaKind`를 조작해도 그대로 저장됩니다.

### 규칙

- 클라이언트가 보낸 메타데이터는 **재계산하거나 검증**한다
- 저장 전에 서버가 판정한 값을 쓴다
- Tika가 이미 의존성에 있으므로 내용 기반 판정이 가능하다

## SC-002 권한 판정을 빠뜨리지 않는다

### 왜 특히 중요한가 `확인됨`

이 프로젝트는 `SecurityConfig`가 대부분 `permitAll`이고
**실제 판정을 도메인에 맡깁니다.** 판정 호출을 잊으면 아무도 막지 않습니다.

### 준수 사례 `확인됨`

```java
public Page<PostListItemResponse> listPosts(...) {
    Board board = boardRepository.findBySlug(boardSlug).orElseThrow();
    Optional<Member> actor = actorOf(user);
    board.checkReadable(actor);      // ← 판정
    ...
}
```

모든 게시글·댓글 경로에 이 호출이 있습니다.

### 위반 사례 `확인됨`

`FileController`와 `FileStorageService`에 **어떤 권한 판정도 없습니다.**
`POST /api/files`가 `permitAll`이라 누구나 업로드할 수 있습니다.

`PostService.createPost`가 `board.allowsAttachment`를 검사하지 않습니다.
댓글은 `CommentService`가 `allowsComment`를 검사하는데 첨부만 빠졌습니다.

### 규칙

새 엔드포인트를 만들 때 셋 중 하나를 **반드시** 한다.

1. `SecurityConfig`에서 `authenticated()`로 막는다
2. 서비스에서 도메인 판정을 호출한다
3. 공개가 의도임을 판단하고 주석으로 남긴다

## SC-003 비밀값을 저장·기록하지 않는다

### 준수 사례 `확인됨`

**토큰 원문을 저장하지 않습니다.**

```java
String rawToken = UUID.randomUUID().toString();
VerificationToken token = VerificationToken.emailVerify(
        member, TokenHasher.sha256(rawToken), Duration.ofHours(24));
```

(`member/application/SignupService.java:40-42`)

DB에는 SHA-256 해시만 들어가고 원문은 메일 링크에만 존재합니다.

**비밀번호는 `DelegatingPasswordEncoder`로 해시합니다.**
알고리즘 접두사가 붙어 나중에 교체할 수 있습니다.

### 위반 사례 `확인됨`

| 위반 | 위치 |
|---|---|
| DB 비밀번호 평문 커밋 | `application.yml`, `docker-compose.yml` |
| SQL 로그 항상 켜짐 | `show-sql: true` |
| 메일 본문(토큰 포함)이 로그에 | `LoggingMailSender` 폴백 시 |
| 비밀번호를 쿼리 파라미터로 | `DELETE /api/posts/{id}?guestPassword=` |

### 규칙

- 비밀값은 `.env`로 주입한다 (`.env.example`에는 양식만)
- 로그에 비밀번호·토큰·인증 정보를 남기지 않는다
- 민감값을 URL·쿼리 파라미터에 넣지 않는다
- `MAIL_DEBUG`는 디버깅 후 반드시 끈다

## SC-004 실패를 조용히 삼키지 않는다

### 위반 사례 — 이 프로젝트의 대표적 문제 `확인됨`

**CAPTCHA 검증**

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return false;
} catch (IOException | RuntimeException e) {
    return false;
}
```

(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:60-67`)

`false`를 반환하는 것은 **안전한 방향**입니다(fail-closed).
문제는 **로그가 전혀 없다**는 것입니다.

네트워크 오류인지, 잘못된 시크릿인지, 실제 봇인지 구분할 수 없습니다.
가입이 안 된다는 신고를 받아도 원인을 찾을 수 없습니다.

**메일 발송**

```java
} catch (RuntimeException e) {
    log.error("메일 발송에 실패했습니다. to={} subject={}", to, subject, e);
}
```

(`member/application/SignupMailListener.java:78-81`)

로그는 남지만 **사용자와 호출자는 모릅니다.**
비동기 리스너라 예외가 전달되지 않습니다.

이것이 `todo.md`의 "발송된 메일이 없음"이 조용히 지속된 이유입니다.

### 규칙

- 예외를 삼킬 때는 **반드시 로그를 남긴다**
- fail-closed는 좋지만 **왜 닫혔는지 알 수 있어야** 한다
- 사용자에게 알릴 수 없는 실패는 **기록으로 남긴다** (DB 또는 메트릭)

## SC-005 오류에 내부 정보를 담지 않는다

### 현재 상태 `확인됨`

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("BAD_REQUEST", e.getMessage()));   // ← 그대로 노출
}
```

(`common/GlobalExceptionHandler.java:52-56`)

현재 이 경로로 나가는 메시지는 모두 의도된 한국어 문구입니다.
**하지만 앞으로 내부 정보를 담은 예외가 추가되면 그대로 새어나갑니다.**

### 더 큰 문제 — 미처리 예외 `확인됨`

`NoSuchElementException`, `RuntimeException`, `SecurityException`에
핸들러가 없어 **Spring 기본 500 응답**이 나갑니다.

```
GET /api/posts/999999  →  500
```

404여야 할 것이 500이고, 기본 응답에 예외 정보가 실릴 수 있습니다.

### 규칙

- 리소스 없음은 **404**로 반환한다
- 미처리 예외는 일반화된 메시지로 감싼다
- 계정 존재 여부를 오류 메시지로 노출하지 않는다
- 오류 메시지는 한국어로 (`CommentService`의 영어 메시지는 예외)

## SC-006 SQL 인젝션 — 파라미터 바인딩

### 준수 사례 `확인됨`

네이티브 쿼리도 파라미터 바인딩을 씁니다.

```java
@Query(value = """
    INSERT INTO post_view_log (post_id, member_id, viewed_on)
    VALUES (:postId, :memberId, :viewedOn)
    ON CONFLICT DO NOTHING
    """, nativeQuery = true)
int tryRecord(@Param("postId") Long postId, ...);
```

키워드 검색도 JPQL 바인딩입니다.

```java
@Query("SELECT p FROM Post p WHERE p.board.slug = :boardSlug AND p.deletedAt IS NULL AND " +
       "(COALESCE(:keyword, '') = '' OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%)")
```

**문자열 연결로 SQL을 만드는 곳이 없습니다.** 잘 지켜지고 있습니다.

### 규칙

- SQL·JPQL에 사용자 입력을 문자열 연결로 넣지 않는다
- 네이티브 쿼리도 `@Param` 바인딩을 쓴다

## SC-007 경로 조작 방어

### 준수 사례 `확인됨`

```java
Path filePath = rootPath.resolve(storedName).normalize();
if (!filePath.startsWith(rootPath)) {
    throw new SecurityException("Path traversal attempt");
}
```

(`attachment/application/FileStorageService.java:70-73`)

`normalize()` **후** `startsWith`로 검사하는 것이 올바른 순서입니다.
정규화 전에 검사하면 `../`가 남아 우회됩니다.

### 남은 문제 `확인됨`

`SecurityException`에 핸들러가 없어 500이 되고, 별도 로그도 없습니다.
공격 시도가 정상 오류율에 섞여 탐지되지 않습니다.

### 규칙

- 경로 조합 후 반드시 `normalize()` + `startsWith` 검사
- 사용자 입력을 파일명에 직접 쓰지 않는다 (현재 UUID 사용 — 올바름)
- 공격 시도는 **로그로 남긴다**

## SC-008 XSS 방어

### 준수 사례 `확인됨`

**SVG를 이미지로 분류하지 않습니다.**

```java
public static MediaKind from(String contentType) {
    if ("image/svg+xml".equals(contentType)) return FILE; // SVG는 스크립트 실행 가능
    ...
}
```

(`attachment/domain/MediaKind.java:7`)

`PRD.md` 2.5가 "확장자 기반 미디어 판단은 XSS 경로"라고 지적한 결과입니다.

**다운로드 시 `Content-Type`을 재판정합니다.**

```java
String contentType = Files.probeContentType(path);
```

(`attachment/adapter/in/web/FileController.java:57`)

업로드 시 저장된(신뢰할 수 없는) 값을 쓰지 않습니다.

### 남은 위험 `확인됨`

| 위험 | 상태 |
|---|---|
| `.html` 업로드 | 막히지 않음. `inline`으로 열면 같은 오리진에서 실행 |
| CSP 헤더 | 없음 |
| `X-Content-Type-Options: nosniff` | 없음 |
| 첨부 서빙 도메인 분리 | 없음 |
| `react-markdown`에 `rehype-raw` | **없음 (안전)** — 추가하지 말 것 |

### 규칙

- 마크다운 렌더링에 원시 HTML을 허용하지 않는다
- 업로드 파일에 실행 가능 형식을 허용하지 않거나 항상 `attachment`로 내린다
- 다운로드 응답에 `nosniff`를 붙인다

## SC-009 도메인이 규칙을 소유한다

### 준수 사례 — 결정 D5 `확인됨`

정규화 규칙이 **한 곳에만** 있습니다.

```java
public static String normalizeNickname(String nickname) {
    if (nickname == null) return null;
    String trimmed = nickname.trim();
    return trimmed.isEmpty() ? null : trimmed;
}
```

(`member/domain/Member.java:58-64`)

조회(`MemberController.checkNickname`)와 저장(`Member.pending`)이 공유합니다.

규칙이 갈라졌을 때 실제로 버그가 났습니다 —
조회는 원본, 저장은 `trim`이라 `" 단팥빵"`이 "사용 가능"으로 보인 뒤 409가 났습니다(`5cca82f`).

### 미준수 `확인됨`

**아이디(`username`)에는 이 규칙이 없습니다.**
조회도 저장도 정규화하지 않아 현재는 일치하지만,
앞뒤 공백이 든 아이디가 만들어질 수 있습니다.

### 규칙

- 검증 규칙은 도메인 객체의 정적 메서드로 두고 모든 경로가 공유한다
- 조회 경로와 저장 경로가 **같은 규칙**을 쓴다

## SC-010 DB 제약을 최종 방어선으로

### 준수 사례 `확인됨`

애플리케이션 검사에만 의존하지 않습니다.

| 규칙 | DB 제약 |
|---|---|
| 아이디·이메일 유일 | UNIQUE |
| 닉네임 유일 (선택 항목) | 부분 UNIQUE 인덱스 |
| 회원/비회원 글 배타 | CHECK (`post_author_ck`) |
| 좋아요 1회 | 복합 PK |
| 조회수 하루 1회 | 복합 PK + `ON CONFLICT` |

중복 확인 API는 **편의 기능**이고 방어선이 아닙니다(`PRD.md` 2.4).

### 규칙

- 유일성·배타성은 DB 제약으로 표현한다
- 애플리케이션 검사는 UX용이며 방어선으로 여기지 않는다
- 동시성이 관련된 규칙은 `ON CONFLICT`처럼 원자적 연산으로 표현한다

## SC-011 트랜잭션 경계와 외부 호출

### 준수 사례 `확인됨`

외부 호출(SMTP)을 **커밋 이후로** 미룹니다.

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSignupCompleted(SignupCompleted event) { ... }
```

메일 실패가 가입 트랜잭션을 되돌리지 않습니다.
느린 SMTP가 DB 커넥션을 붙잡지도 않습니다.

### 반례 — CAPTCHA `확인됨`

CAPTCHA HTTP 호출은 **트랜잭션 안**에 있습니다
(`SignupService.signup`이 `@Transactional`).

의도적입니다 — 검증 실패 시 회원이 생성되면 안 되기 때문입니다.
다만 provider가 느리면 DB 커넥션이 최대 3초 잡힙니다.

타임아웃이 3초로 짧게 잡혀 있어 현재는 감수할 만합니다.

### 규칙

- 외부 호출은 가능하면 트랜잭션 밖으로
- 트랜잭션 안에서 호출해야 한다면 **짧은 타임아웃을 반드시** 건다
- `@Async` 리스너 안의 예외는 반드시 잡아서 기록한다

## SC-012 주석으로 함정을 남긴다

이 프로젝트의 강점입니다. **무엇을 하는지가 아니라 왜 그런지**를 적습니다.

```java
// spa() = CookieCsrfTokenRepository.withHttpOnlyFalse() + SpaCsrfTokenRequestHandler.
// (기본 XorCsrfTokenRequestAttributeHandler는 마스킹된 토큰을 기대하므로
//  쿠키 값을 그대로 보내는 SPA에서 403이 난다.)
```

```yaml
# clean(스키마 전체 삭제)을 막는다. 이전 설정은 ... 그 조합에서는 마이그레이션 검증이
# 실패하면 앱이 뜨는 것만으로 대상 DB 가 통째로 지워진다.
```

### 규칙

- 보안 관련 결정에는 **위험과 대안**을 함께 적는다
- 되돌리면 안 되는 설정에는 되돌렸을 때의 결과를 적는다

## 코드 리뷰 체크리스트 `제안`

새 코드를 볼 때 확인할 것입니다.

- [ ] 권한 판정이 있는가 (SC-002) — 이 프로젝트에서 가장 자주 빠짐
- [ ] 클라이언트 값을 그대로 저장하지 않는가 (SC-001)
- [ ] 예외를 삼키면서 로그를 남기는가 (SC-004)
- [ ] 없는 리소스에 404를 반환하는가 (SC-005)
- [ ] 비밀값이 로그·URL에 없는가 (SC-003)
- [ ] SQL에 문자열 연결이 없는가 (SC-006)
- [ ] 규칙이 한 곳에만 있는가 (SC-009)
- [ ] DB 제약으로 표현할 수 있는 규칙인가 (SC-010)
- [ ] 외부 호출에 타임아웃이 있는가 (SC-011)
- [ ] 왜 그렇게 했는지 주석이 있는가 (SC-012)

## 관련 문서

- [threat-model.md](threat-model.md) — 위협별 상세
- [security-requirements.md](security-requirements.md) — 요구사항
- [security-test-plan.md](security-test-plan.md) — 검증
- [../technology/api/api-guidelines.md](../technology/api/api-guidelines.md) — API 규약
- [../technology/developer-guide.md](../technology/developer-guide.md) — 개발 가이드
