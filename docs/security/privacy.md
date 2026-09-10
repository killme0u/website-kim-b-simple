# 개인정보 처리

> 상태: 처리 현황은 `확인됨`(코드·스키마 기준). 개선안은 `제안`입니다.
> **법적 준수 여부에 대한 판단이 아닙니다.** 법무 검토가 별도로 필요합니다.

## 수집 항목 `확인됨`

`member` 테이블 기준입니다.

| 항목 | 컬럼 | 필수 | 수집 근거 | 실제 사용처 |
|---|---|---|---|---|
| 로그인 아이디 | `username` | 예 | 인증 | 인증 + **작성자명 공개 노출** |
| 비밀번호 | `password_hash` | 예 | 인증 | 인증 (bcrypt) |
| 실명 | `name` | 예 | 본인 확인 | `GET /api/me` 응답 |
| 닉네임 | `nickname` | 아니오 | 표시명 | **거의 없음** |
| 이메일 | `email` | 예 | 인증·복구 | 메일 발송 |
| 휴대전화 | `phone` | **예** | 불명 | **없음** |

### 목적 없는 수집 — `phone` `미결정`

| 층 | 상태 |
|---|---|
| DB | `NOT NULL` |
| API | `@NotBlank` |
| `UI.md` | **선택 항목**으로 그림 |
| 읽는 코드 | **없음** |

SMS 발송도, 본인 확인도, 조회 API도 없습니다.
**수집만 하고 쓰지 않는 필수 항목**입니다.

`todo.md`에 UI-구현 불일치로 등록되어 있습니다.

개인정보 최소 수집 원칙에 어긋나며, 유출 시 피해만 있고 얻는 것이 없습니다.

### 사용되지 않는 수집 — `nickname` `확인됨`

`V3`에서 표시명 목적으로 추가했는데 실제로는 쓰이지 않습니다.

| 위치 | 실제 표시 |
|---|---|
| `PostResponse.authorName` | `username` |
| `PostListItemResponse.authorName` | `username` |
| `CommentResponse.authorName` | `username` |
| `MeResponse` | **`nickname` 필드 자체가 없음** |

닉네임을 입력해도 어디에도 나타나지 않습니다.

## 동의 `확인됨`

**동의 기록이 남지 않습니다.**

```java
private Boolean termsAccepted;
```

(`member/adapter/in/web/dto/SignupCommand.java:37`)

| 항목 | 상태 |
|---|---|
| 필드 존재 | 예 |
| `@NotNull` 검증 | **없음** |
| 서비스가 읽음 | **없음** — `SignupService`가 무시 |
| DB 저장 | **없음** — 컬럼 자체가 없음 |
| 동의 시각 | 없음 |
| 약관 버전 | 없음 |
| 개인정보처리방침 문서 | **저장소에 없음** |

화면에서 동의를 받아도 서버는 그것을 확인하지도 기록하지도 않습니다.
나중에 "동의를 받았는가"를 증빙할 수단이 없습니다.

### 대응 `제안`

필드가 이미 있으므로 작은 변경입니다.

```java
@NotNull
@AssertTrue(message = "약관에 동의해야 가입할 수 있습니다.")
private Boolean termsAccepted;
```

```sql
-- V5__add_member_terms_agreement.sql
ALTER TABLE member ADD COLUMN terms_agreed_at TIMESTAMPTZ;
```

## 이용 목적별 처리 `확인됨`

| 목적 | 사용 항목 | 처리 |
|---|---|---|
| 인증 | `username`, `password_hash` | bcrypt 비교 |
| 이메일 인증 | `email` | 토큰 링크 발송 |
| 비밀번호 재설정 | `email` | 토큰 링크 발송 |
| 아이디 찾기 | `email` | **`username` 평문 발송** |
| 게시글·댓글 표시 | `username` | **공개 노출** |
| 조회수 중복 방지 | 회원 ID | `post_view_log` 기록 |

### 아이디 찾기의 본인 확인 `미결정`

```java
memberRepository.findByEmail(command.getEmail())
        .filter(member -> member.getStatus() != MemberStatus.DELETED)
        .ifPresent(member -> events.publishEvent(
                new UsernameRecoveryRequested(member.getEmail(), member.getUsername())));
```

(`member/application/UsernameRecoveryService.java:20-23`)

본인 확인이 **이메일 소유뿐**입니다. CAPTCHA도 없습니다.

가입·비밀번호 재설정에는 CAPTCHA가 있는데 여기만 없습니다.
`plan.md` 선결 결정 #5가 이 방식을 확정 대상으로 남겨두었습니다.

### 조회 기록이 행동 로그가 됩니다 `확인됨`

`post_view_log`의 본래 목적은 조회수 중복 방지입니다.
그러나 `(post_id, member_id, viewed_on)` 조합이 쌓이면서
**어떤 회원이 언제 어떤 글을 봤는지**의 기록이 됩니다.

| 항목 | 상태 |
|---|---|
| 사용자 고지 | 없음 |
| 보존 기간 | **미정의** — 영구 |
| 조회 API | 없음 (내부 전용) |
| 정리 배치 | 없음 |

기술적 필요에서 만들어진 테이블이 사실상의 열람 이력이 되었습니다.
목적 외 이용 소지가 있습니다. `미결정`

**최소한 보존 기간을 두는 것이 안전합니다.**
조회수 판정에는 당일 기록만 필요합니다.

## 열람·정정·삭제 `확인됨`

| 권리 | 지원 | 방법 |
|---|---|---|
| 열람 | 부분 | `GET /api/me`, `/api/me/posts`, `/api/me/comments` |
| 정정 | **불가** | 회원 정보 수정 API가 없음 |
| 삭제(탈퇴) | **불가** | `DELETED` 전환 코드 없음 |
| 처리 정지 | **불가** | |
| 이동(다운로드) | **불가** | |

### 열람의 한계 `확인됨`

`GET /api/me`가 반환하는 것:
`id`, `username`, `name`, `email`, `status`, `role`, `mustChangePassword`

**빠진 것**: `nickname`, `phone`, `createdAt`

수집한 항목 중 일부를 본인이 확인할 수 없습니다.

### 정정 불가 `확인됨`

이메일·이름·닉네임·전화번호를 바꾸는 API가 없습니다.
비밀번호만 재설정 토큰으로 바꿀 수 있습니다.

가입 시 오타를 내면 **고칠 방법이 없습니다.**
이메일이 틀리면 인증 메일도 못 받고 복구도 못 합니다.

### 탈퇴 불가 — 구조적 문제 `확인됨`

`MemberStatus.DELETED`는 정의만 있고 전환 코드가 없습니다.

게다가 `member` 행을 물리적으로 지울 수도 없습니다.

| 자식 테이블 | FK 옵션 | 회원 삭제 시 |
|---|---|---|
| `verification_token` | `ON DELETE CASCADE` | 함께 삭제 |
| `post_like` | `ON DELETE CASCADE` | 함께 삭제 |
| `post_view_log` | `ON DELETE CASCADE` | 함께 삭제 |
| `post` | **CASCADE 없음** | **FK 위반으로 실패** |
| `comment` | **CASCADE 없음, NOT NULL** | **FK 위반으로 실패** |

**글이나 댓글을 쓴 회원은 삭제할 수 없습니다.**

### 대응 — 익명화 `제안`

스키마 변경 없이 가능한 방법입니다.

```
status = DELETED
username = 'deleted_' || id        (UNIQUE 유지)
email    = 'deleted_' || id || '@invalid'   (UNIQUE 유지)
name     = '탈퇴한 사용자'
nickname = NULL
phone    = ''
password_hash = <무작위 값>
```

글·댓글은 유지되어 다른 사용자의 대화 맥락이 보존됩니다.
`authorName`이 `username`을 쓰므로 표시도 자동으로 바뀝니다
(다만 `deleted_123`이 보이므로 표시 로직 조정 필요).

`DELETED` 상태는 로그인이 차단되고(`isEnabled()`),
비밀번호 재설정·아이디 찾기에서도 제외됩니다. **판정 로직은 이미 준비되어 있습니다.**

## 제3자 제공 `확인됨`

| 대상 | 제공 항목 | 목적 |
|---|---|---|
| SMTP 제공자 | 이메일 주소, 이름(본문), **아이디**(찾기 메일) | 메일 발송 |
| CAPTCHA 제공자 | **클라이언트 IP** (`remoteip`) | 봇 판정 |

### CAPTCHA에 IP를 보냅니다 `확인됨`

```java
String requestBody = objectMapper.writeValueAsString(Map.of(
        "secret", properties.getSecret(),
        "response", token,
        "remoteip", remoteAddress == null ? "" : remoteAddress
));
```

(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:52-56`)

`HttpServletRequest.getRemoteAddr()`의 값입니다
(`MemberController.java:40`).

| 항목 | 상태 |
|---|---|
| 사용자 고지 | 없음 |
| provider 확정 | **미정** — 어느 나라 서버인지 모름 |
| 국외 이전 고지 | 없음 |

provider가 정해지면 국외 이전 여부와 고지 필요성을 확인해야 합니다. `미결정`

### 프록시 뒤에서는 IP가 부정확 `확인됨`

`getRemoteAddr()`은 리버스 프록시가 있으면 **프록시 IP**를 반환합니다.
`X-Forwarded-For`를 보지 않습니다.

CAPTCHA 판정 정확도가 떨어지고, 로그의 IP도 무의미해집니다.
`server.forward-headers-strategy` 설정이 필요합니다. `제안`

## 로그에 남는 개인정보 `확인됨`

| 조건 | 내용 |
|---|---|
| `show-sql: true` (**항상**) | SQL 파라미터에 이메일·이름 |
| `LoggingMailSender` 폴백 | **메일 본문 전체** — 이메일, 이름, 인증 토큰 |
| `MAIL_DEBUG=true` | SMTP 대화 + 인증 정보 |
| 메일 발송 실패 | `to=` 수신자 주소 (`SignupMailListener.java:80`) |

로그 보존 기간도, 접근 통제도 없습니다.
컨테이너 stdout으로만 나가 재시작 시 사라지는 것이 역설적으로 유일한 보호입니다.

## 우선 조치 `제안`

법적 판단이 필요 없고 명백히 빠진 것부터입니다.

| 순위 | 조치 | 비용 |
|---|---|---|
| 1 | `termsAccepted` 검증 + 동의 시각 저장 | 낮음 |
| 2 | `phone`을 선택으로 또는 제거 | 낮음 |
| 3 | `authorName`을 닉네임으로 (아이디 노출 차단) | 낮음 |
| 4 | `show-sql`을 환경 변수로 | 매우 낮음 |
| 5 | 탈퇴(익명화) 구현 | 중간 |
| 6 | 회원 정보 수정 API | 중간 |
| 7 | `post_view_log` 보존 기간 | 중간 |
| 8 | 개인정보처리방침 작성 | 법무 검토 |
| 9 | HTTPS (전송 중 보호) | 인프라 |

3번은 [threat-model.md](threat-model.md) T-010과 같은 조치입니다 —
프라이버시와 보안 양쪽에 효과가 있습니다.

## 관련 문서

- [data-classification.md](data-classification.md) — 등급 분류
- [../technology/data/data-retention.md](../technology/data/data-retention.md) — 보존·파기
- [../business/compliance-matrix.md](../business/compliance-matrix.md) — 규제 대응
- [threat-model.md](threat-model.md) — T-010
