# RB-001 메일이 발송되지 않음

> 상태: **현재 열린 문제입니다.** `todo.md`(2026-09-10)에 "이메일 인증 기능의 정상 동작 확인 필요 / 발송된 메일이 없음"으로 등록되어 있습니다.
> 절차는 `확인됨` — 코드 경로에 근거합니다.

## 증상

- 회원가입은 성공하는데(201) 인증 메일이 오지 않음
- 비밀번호 재설정·아이디 찾기 메일도 오지 않음
- 애플리케이션은 오류를 표시하지 않음

## 영향 범위 `확인됨`

메일이 안 오면 계정이 `PENDING`에 머뭅니다. 그런데 `PENDING`으로도 로그인은 됩니다
(`CustomUserDetails.isEnabled()`가 `PENDING`을 허용).

| 기능 | 영향 |
|---|---|
| 로그인 | **가능** — `PENDING`도 허용 |
| Q&A·자료실 | **가능** — 게시판은 로그인 여부만 봄 |
| 비밀번호 재설정 | **불가** — 링크를 받을 수 없음 |
| 아이디 찾기 | **불가** |

따라서 신규 가입자가 당장 막히지는 않지만,
**비밀번호를 잊으면 복구할 방법이 전혀 없습니다.**

## 왜 조용히 실패하는가 `확인됨`

구조적 이유가 있습니다.

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSignupCompleted(SignupCompleted event) { ... }

private void send(String to, String subject, String template, Map<String, Object> variables) {
    try {
        mailSender.send(to, subject, templateEngine.process(template, context));
    } catch (RuntimeException e) {
        log.error("메일 발송에 실패했습니다. to={} subject={}", to, subject, e);
    }
}
```

(`member/application/SignupMailListener.java:35-39, 74-82`)

| 사실 | 결과 |
|---|---|
| 커밋 후 실행 | 가입 트랜잭션은 이미 성공 |
| `@Async` | 예외가 호출자에게 전달되지 않음 |
| `catch` 후 `log.error` | **로그에만 남고 아무도 모름** |
| 발송 결과 미저장 | 나중에 확인할 방법 없음 |

**사용자는 201을 받고, 관리자는 로그를 보지 않으면 모릅니다.**

## 빠른 확인 (1분) `확인됨`

기동 시 어느 발송기가 선택됐는지부터 봅니다.

```bash
docker compose logs app | grep -iE "SMTP (메일 발송을 사용|설정이 비어)"
```

| 출력 | 다음 단계 |
|---|---|
| `SMTP 설정이 비어 있어(host='', username 설정됨=false) ...` | → **원인 A** |
| `SMTP 메일 발송을 사용합니다. host=smtp.naver.com from=...` | → **원인 B 이후** |
| (아무것도 안 나옴) | 앱이 기동하지 않았거나 로그가 유실됨 |

이어서 실제 발송 실패 로그를 봅니다.

```bash
docker compose logs app | grep "메일 발송에 실패"
```

## 원인 A — 로그 전용 발송기로 폴백됨 `확인됨`

가장 흔한 경우입니다. **설정이 비어 실제 발송을 하지 않는 상태**입니다.

### 판정 조건

```java
if (sender == null || !StringUtils.hasText(host) || !StringUtils.hasText(username)) {
    return new LoggingMailSender();
}
```

(`config/MailConfig.java:38-45`)

`MAIL_SMTP_HOST` **또는** `MAIL_USERNAME` 중 하나라도 비면 폴백합니다.

### 확인

```bash
# 컨테이너 환경 변수
docker compose exec app printenv | grep -E "^MAIL_"

# 호스트 .env
grep -E "^MAIL_" .env
```

### 자주 하는 실수 `확인됨`

`.env`는 **dotenv가 아니라 `.properties` 문법**으로 읽힙니다(`.env.example` 경고).

| 실수 | 결과 |
|---|---|
| `MAIL_PASSWORD="abcd"` | 따옴표까지 값이 됨 |
| `MAIL_SMTP_HOST=` (줄은 두고 값만 비움) | 빈 문자열 → 폴백 |
| 값에 한글 | ISO-8859-1로 읽혀 깨짐 |
| `.env` 파일이 아예 없음 | `optional:`이라 조용히 넘어감 |

### 대응

```bash
# 1. .env 준비
cp .env.example .env

# 2. 최소한 아래 셋을 채움
#    MAIL_SMTP_HOST=smtp.naver.com
#    MAIL_USERNAME=<네이버아이디>@naver.com
#    MAIL_PASSWORD=<앱 비밀번호>       ← 로그인 비밀번호가 아님

# 3. 재기동
docker compose up -d --force-recreate app

# 4. 확인
docker compose logs app | grep -i "SMTP 메일 발송을 사용"
```

### 폴백 상태에서 인증을 마치는 방법 `확인됨`

개발 중이라 SMTP를 설정하고 싶지 않다면,
`LoggingMailSender`가 남긴 본문에서 링크를 꺼내 쓸 수 있습니다.

```bash
docker compose logs app | grep -B 5 -A 30 "verify-email"
```

HTML 본문 안의 `{APP_BASE_URL}/verify-email?token=...`을 브라우저에 붙여넣습니다.
(`UI.md`의 "개발 환경에서의 이메일 인증 확인" 절)

## 원인 B — SMTP 인증 실패 `확인됨`

발송기는 SMTP인데 발송이 실패하는 경우입니다.

### 확인

```bash
docker compose logs app | grep -A 30 "메일 발송에 실패"
```

스택트레이스에서 원인을 봅니다.

| 예외 메시지 단서 | 원인 |
|---|---|
| `Authentication failed` / `535` | 계정·비밀번호 오류 |
| `Connection timed out` | 방화벽·포트 차단 |
| `Could not connect to SMTP host` | 호스트명 오류 |
| `553` / `Sender address rejected` | 발신자 주소 불허 |

### 원인 B-1 — 앱 비밀번호를 쓰지 않음

네이버 메일은 **로그인 비밀번호가 아니라 앱 비밀번호**를 요구합니다.
또한 네이버 메일 > 환경설정 > POP3/IMAP 설정에서
"POP3/SMTP 사용함"을 먼저 켜야 합니다(`.env.example`).

### 원인 B-2 — 포트와 암호화 방식 불일치 `확인됨`

```
465 → MAIL_SMTP_SSL=true,  MAIL_SMTP_STARTTLS=false   (암묵적 SSL)
587 → MAIL_SMTP_SSL=false, MAIL_SMTP_STARTTLS=true    (STARTTLS)
```

포트만 바꾸고 두 플래그를 안 바꾸면 실패합니다.

### 원인 B-3 — 발신자 주소 불허 `확인됨`

`.env.example`의 기본값 조합에 주의가 필요합니다.

```
MAIL_SMTP_HOST=smtp.naver.com
MAIL_FROM_ADMIN=killme0u@gmail.com    ← 네이버 SMTP 로 gmail 주소 발신
```

네이버 SMTP는 보통 `MAIL_USERNAME`과 **같은 주소만** 발신자로 허용합니다.
`.env.example`에도 그렇게 적혀 있습니다.

대응: `MAIL_FROM_ADMIN`을 `MAIL_USERNAME`과 같은 주소로 맞춥니다.

### 원인 B-4 — 타임아웃 `확인됨`

연결·읽기·쓰기 타임아웃이 각 5초입니다(`application.yml`).
네트워크가 느리거나 방화벽이 조용히 패킷을 버리면 여기서 걸립니다.

### 상세 진단 — `MAIL_DEBUG` `확인됨`

```bash
# 임시로 켜기
MAIL_DEBUG=true docker compose up -d --force-recreate app
docker compose logs -f app
```

SMTP 대화 전체가 stdout에 나옵니다.

**끝나면 반드시 끄세요.** 인증 정보가 로그에 남습니다.
`application.yml` 주석에도 "디버깅할 때만 켠다"고 적혀 있습니다.

## 원인 C — 발송은 됐으나 도달하지 않음 `제안`

로그에 실패가 없고 발송기가 SMTP인데도 메일이 안 보이는 경우입니다.

| 확인 | 방법 |
|---|---|
| 스팸함 | 수신자 스팸함 확인 |
| 발송 한도 | 네이버 등 개인 계정은 일일 한도가 있음 |
| SPF/DKIM/DMARC | 설정이 없어 스팸 분류될 가능성 |
| 수신 도메인 정책 | 회사 메일 등이 외부 메일을 차단 |

이 경우 애플리케이션 쪽에서 할 수 있는 것이 없습니다.
운영에서는 전용 발송 서비스(SES, SendGrid 등)를 쓰는 것이 안전합니다. `제안`

## 원인 D — 링크가 잘못됨 `확인됨`

메일은 왔는데 링크를 눌러도 안 되는 경우입니다.

```java
private String link(String path, String rawToken) {
    return "%s%s?token=%s".formatted(
            properties.getBaseUrl(), path, URLEncoder.encode(rawToken, UTF_8));
}
```

(`member/application/SignupMailListener.java:84-87`)

`properties.getBaseUrl()`이 `APP_BASE_URL`입니다. 기본값이 `http://localhost:8080`이라
**외부 사용자는 접속할 수 없습니다.**

```bash
docker compose exec app printenv APP_BASE_URL
```

대응: 외부에서 접근 가능한 실제 주소로 설정하고 재기동합니다.

## 관련 확인 — 토큰 상태 `확인됨`

메일 문제와 별개로 토큰 자체를 확인할 수 있습니다.

```bash
# 최근 발급된 인증 토큰
docker exec board-postgres psql -U board_user -d board_db -c "
SELECT vt.id, m.email, vt.purpose, vt.expires_at, vt.used_at
  FROM verification_token vt JOIN member m ON m.id = vt.member_id
 ORDER BY vt.id DESC LIMIT 10;"

# 인증 대기 계정 수
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT count(*) FROM member WHERE status = 'PENDING';"
```

| 관찰 | 해석 |
|---|---|
| 토큰이 생성됨, `used_at`이 전부 `NULL` | 발송 또는 도달 문제 |
| 토큰이 생성되지 않음 | 가입 자체가 실패 (CAPTCHA 등) |
| `PENDING`이 계속 쌓임 | 메일 문제가 지속 중 |

## 재발송 `확인됨`

사용자에게 재발송을 안내할 수 있습니다.

```bash
curl -X POST http://localhost:8080/api/members/verify-email/resend \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```

**응답은 계정 유무와 무관하게 202입니다**(계정 열거 방지).
202를 받아도 실제로 보냈다는 뜻이 아닙니다.

주의: 재발송에 횟수 제한이 없고 기존 토큰을 무효화하지 않습니다.
호출할 때마다 유효한 토큰이 하나씩 늘어납니다.

## 예방 `제안`

이 장애가 조용히 지나가지 않게 하려면:

| 조치 | 효과 |
|---|---|
| **메일 발송 결과를 DB에 기록** | 사후 확인·집계 가능 |
| 기동 시 위험 설정 경고 | 폴백 상태를 눈에 띄게 |
| 발송 실패 알림 | 즉시 인지 |
| 프론트엔드에 "메일이 안 오면 재발송" 안내 | 사용자 자가 해결 |
| 발송 재시도 | 일시적 장애 흡수 |

**첫 번째가 가장 중요합니다.** 지금은 "메일이 갔는가"라는 질문에
답할 수 있는 데이터가 어디에도 없습니다.

## 관련 문서

- [README.md](README.md) — 공통 진단
- [../../api/integration-contracts.md](../../api/integration-contracts.md) — IC-001 SMTP 계약
- [../environments.md](../environments.md) — `MAIL_*` 환경 변수
- [../observability.md](../observability.md) — 로그 현황
- [../../../features/use-cases/UC-001-user-signup.md](../../../features/use-cases/UC-001-user-signup.md) — 대체 흐름 A3
