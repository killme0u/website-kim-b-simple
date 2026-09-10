# 외부 연동 계약

> 상태: 코드에서 확인한 호출 형태는 `확인됨`, provider 확정 사항은 `미결정`입니다.

이 시스템이 밖으로 나가는 연동은 셋입니다 — **SMTP, CAPTCHA, PostgreSQL**.
인바운드 연동(웹훅 등)은 없습니다.

## IC-001 SMTP `확인됨`

### 계약

| 항목 | 값 |
|---|---|
| 방향 | 아웃바운드만 |
| 프로토콜 | SMTP over SSL(465) 또는 STARTTLS(587) |
| 구현 | Spring `JavaMailSender` |
| 포트 인터페이스 | `MailSenderPort.send(to, subject, htmlBody)` |
| 호출 시점 | 트랜잭션 커밋 후 비동기 |
| 본문 형식 | HTML (Thymeleaf 렌더링) |

### 설정 `확인됨`

| 환경 변수 | 기본값 | 용도 |
|---|---|---|
| `MAIL_SMTP_HOST` | (빈 값) | 비면 로그 전용 폴백 |
| `MAIL_SMTP_PORT` | `465` | 465=암묵적 SSL, 587=STARTTLS |
| `MAIL_SMTP_SSL` | `true` | 465용 |
| `MAIL_SMTP_STARTTLS` | `false` | 587용 |
| `MAIL_USERNAME` | (빈 값) | 비면 로그 전용 폴백 |
| `MAIL_PASSWORD` | (빈 값) | 네이버는 **앱 비밀번호** |
| `MAIL_FROM_ADMIN` | `no-reply@localhost` | 발신자 |
| `MAIL_DEBUG` | `false` | **인증 정보까지 로그에 남음** |
| `APP_BASE_URL` | `http://localhost:8080` | 메일 링크의 기준 주소 |

타임아웃은 연결·읽기·쓰기 각 5초입니다(`application.yml`).

### 폴백 규칙 `확인됨`

```java
if (sender == null || !StringUtils.hasText(host) || !StringUtils.hasText(username)) {
    log.warn("SMTP 설정이 비어 있어 ... 로그로만 남습니다.");
    return new LoggingMailSender();
}
```

(`config/MailConfig.java:38-47`)

**호스트만이 아니라 계정까지 봅니다.** 이유가 코드 주석에 있습니다 —
`.env.example`을 복사만 하고 자격 증명을 비운 상태에서 기동은 성공한 뒤
가입 시점에야 SMTP 인증 실패로 터지는 상황을 막기 위해서입니다.
`mail.smtp.auth`가 항상 `true`라 계정 없이는 어차피 발송이 불가능합니다.

검증: `MailConfigTest` 3개 (`fallsBackWhenHostIsBlank`,
`fallsBackWhenCredentialsAreBlank`, `usesSmtpWhenFullyConfigured`)

### 발송 종류 `확인됨`

| 이벤트 | 제목 | 템플릿 | 링크 | 만료 |
|---|---|---|---|---|
| `SignupCompleted` | `[BoardSystem] 회원가입 인증을 완료해 주세요` | `mail/email-verification` | `{BASE}/verify-email?token=` | 24시간 |
| `EmailVerificationRequested` | 동일 | 동일 | 동일 | 24시간 |
| `PasswordResetRequested` | `[BoardSystem] 비밀번호 재설정 안내` | `mail/password-reset` | `{BASE}/find-password?token=` | 1시간 |
| `UsernameRecoveryRequested` | `[BoardSystem] 아이디 찾기 안내` | `mail/username-recovery` | `{BASE}/login` | — |

토큰은 `URLEncoder.encode(rawToken, UTF_8)`로 인코딩됩니다
(`member/application/SignupMailListener.java:84-87`).

### 실패 처리 `확인됨`

```java
try {
    mailSender.send(to, subject, templateEngine.process(template, context));
} catch (RuntimeException e) {
    log.error("메일 발송에 실패했습니다. to={} subject={}", to, subject, e);
}
```

(`SignupMailListener.java:74-82`)

| 사실 | 결과 |
|---|---|
| 비동기 리스너라 예외가 호출자에게 전달되지 않음 | 여기서 잡지 않으면 사라짐 |
| 잡아서 로그만 남김 | 사용자는 실패를 모름 |
| 재시도 없음 | 한 번 실패하면 끝 |
| 발송 결과를 저장하지 않음 | 집계·모니터링 불가 |

**`todo.md`의 "발송된 메일이 없음"이 조용히 발생하는 구조입니다.** `미결정`

진단: [../infrastructure/runbooks/RB-001-mail-not-sent.md](../infrastructure/runbooks/RB-001-mail-not-sent.md)

### 계약 위험 `미결정`

| 위험 | 내용 |
|---|---|
| 발신자 제약 | 네이버 SMTP는 보통 `MAIL_USERNAME`과 같은 주소만 발신자로 허용 |
| 발송 한도 | 개인 메일 계정은 일일 한도가 있음. 초과 시 차단 |
| 스팸 분류 | SPF·DKIM·DMARC 설정이 없어 스팸함으로 갈 가능성 |
| `APP_BASE_URL` 미변경 | 링크가 `localhost:8080`을 가리켜 사용자가 접속 불가 |
| `MAIL_DEBUG=true` | SMTP 대화 전체(인증 정보 포함)가 stdout에 남음 |

`.env.example`의 `MAIL_FROM_ADMIN` 기본값이 `killme0u@gmail.com`인데
`MAIL_SMTP_HOST`는 `smtp.naver.com`입니다. 네이버 SMTP로 gmail 주소를
발신자로 쓰면 거부될 가능성이 높습니다. 확인이 필요합니다.

**운영 전환 시 개인 메일 대신 전용 발송 서비스**(SES, SendGrid, Mailgun 등)를
쓰는 것이 안전합니다. `제안`

## IC-002 CAPTCHA `확인됨`

### 계약

| 항목 | 값 |
|---|---|
| 방향 | 아웃바운드만 |
| 프로토콜 | HTTP POST, **JSON 본문** |
| 클라이언트 | `java.net.http.HttpClient` |
| 포트 인터페이스 | `CaptchaVerifier.verify(token, remoteAddress) → boolean` |
| 타임아웃 | `CAPTCHA_TIMEOUT` (기본 3초), 연결·요청 양쪽 |

### 요청·응답 형태 `확인됨`

```
POST {CAPTCHA_ENDPOINT}
Content-Type: application/json

{ "secret": "...", "response": "<토큰>", "remoteip": "<클라이언트 IP>" }
```

성공 판정: 응답 JSON의 `success` 필드가 `true`
(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:48-58`)

### provider 호환성 주의 `미결정`

필드 이름(`secret`, `response`, `remoteip`)과 응답의 `success`는
reCAPTCHA·hCaptcha 계열과 같습니다. 그러나 **본문을 JSON으로 보냅니다.**

reCAPTCHA와 hCaptcha는 `application/x-www-form-urlencoded`를 요구합니다.
이 구현을 그대로 쓰면 두 서비스와 연동되지 않을 가능성이 높습니다.

`plan.md` 선결 결정 #1(운영 CAPTCHA provider)이 확정되면
`ConfiguredCaptchaVerifier`의 인코딩 방식을 함께 확인해야 합니다.

### 동작 모드 `확인됨`

| `CAPTCHA_MODE` | 동작 | 용도 |
|---|---|---|
| `fake` (기본) | `CAPTCHA_EXPECTED_TOKEN`과 문자열 비교 | 개발 |
| `remote` | HTTP 호출 후 `success` 확인 | 운영 |
| 그 외 | 항상 `false` | — |

`remote`인데 `endpoint`나 `secret`이 비어 있으면 `false`를 반환합니다
(안전한 방향의 실패).

### `fake` 모드의 위험 `확인됨`

```
CAPTCHA_MODE=fake
CAPTCHA_EXPECTED_TOKEN=dev-captcha
```

`.env.example`의 기본값입니다. 문자열 `dev-captcha`가 모든 검증을 통과합니다.

**이 상태로 외부에 노출하면 CAPTCHA가 없는 것과 같습니다.**
가입과 비밀번호 재설정 요청이 무제한 자동화에 열립니다. `미결정`

배포 전 확인 목록의 최우선 항목입니다.

### 실패 흡수 `확인됨`

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return false;
} catch (IOException | RuntimeException e) {
    return false;
}
```

(`ConfiguredCaptchaVerifier.java:60-67`)

모든 예외가 `false`가 됩니다. **fail-closed** 설계입니다 — 안전하지만:

| 결과 | 영향 |
|---|---|
| provider 장애 = 가입 전면 중단 | 가용성 저하 |
| **로그가 전혀 남지 않음** | 원인 파악 불가 `미결정` |

네트워크 오류인지, 잘못된 secret인지, 실제 봇인지 구분할 수 없습니다.
최소한 `log.warn`은 남겨야 합니다. `제안`

### 사용 지점 `확인됨`

| API | CAPTCHA |
|---|---|
| `POST /api/members/signup` | 필요 |
| `POST /api/members/password-reset/request` | 필요 |
| `POST /api/members/username-recovery` | **없음** `미결정` |
| `POST /api/auth/login` | 없음 |

아이디 찾기에만 없는 것은 비대칭입니다.
로그인에 없는 것은 무차별 대입 방어가 전혀 없다는 뜻입니다.

## IC-003 PostgreSQL `확인됨`

### 계약

| 항목 | 값 |
|---|---|
| 버전 | 15 |
| 드라이버 | `org.postgresql:postgresql` |
| 접속 | JDBC |
| DB·계정 | `board_db` / `board_user` |
| 스키마 관리 | Flyway (앱이 기동 시 실행) |
| Hibernate DDL | `validate` |

### 접속 주소 결정 `확인됨`

```
1. application.yml 기본값   jdbc:postgresql://${PGSQL_HOST:192.168.29.124}:${PGSQL_PORT:5432}/board_db
2. .env                     PGSQL_HOST / PGSQL_PORT
3. 환경 변수                 SPRING_DATASOURCE_URL (URL 전체를 덮어씀)
```

컨테이너 배포는 3번을 씁니다(`docker-compose.yml`).

### 의존하는 PostgreSQL 전용 기능 `확인됨`

다른 DB로 옮기려면 다시 써야 하는 것들입니다.

| 기능 | 사용처 |
|---|---|
| `INSERT ... ON CONFLICT DO NOTHING` | `PostViewLogRepository.tryRecord` (네이티브 쿼리) |
| 부분 UNIQUE 인덱스 (`WHERE`) | `ux_member_nickname` |
| `TIMESTAMPTZ` | 모든 시각 컬럼 |
| `BIGSERIAL` | 모든 PK |

→ [../adr/ADR-002-postgresql.md](../adr/ADR-002-postgresql.md)

### 안전장치 `확인됨`

```yaml
flyway:
  clean-disabled: true
  clean-on-validation-error: false
jpa:
  hibernate:
    ddl-auto: validate
```

스키마 검증 실패가 **삭제가 아니라 기동 실패**로 드러납니다.

### 계약 위험 `미결정`

| 위험 | 내용 |
|---|---|
| 자격 증명 평문 | `docker-compose.yml`에 `board_password` |
| 5432 호스트 노출 | 같은 네트워크의 다른 기기에서 접근 가능 |
| `depends_on`에 조건 없음 | postgres 준비 전에 app이 떠서 Flyway 실패 가능 |
| 백업 없음 | `board_data` 볼륨 손실 = 전체 손실 |
| 커넥션 풀 설정 없음 | HikariCP 기본값 사용 |

## 연동 없음 — 확인된 사실 `확인됨`

아래는 코드에 흔적이 없습니다.

| 항목 | 상태 |
|---|---|
| OAuth·SSO | 없음 (`plan.md` 선결 결정 #3에서 보류) |
| 오브젝트 스토리지 | 없음. 로컬 파일시스템만 |
| 캐시 서버 (Redis 등) | 없음 |
| 메시지 큐 | 없음. Spring `ApplicationEvent`만 |
| 결제 | 없음 |
| SMS·알림톡 | 없음 (`phone`을 수집하지만 사용처 없음) |
| 외부 검색 엔진 | 없음. DB `LIKE`만 |
| 인바운드 웹훅 | 없음 |
| 모니터링 에이전트 | 없음 |

## 연동 추가 시 규약 `제안`

1. **포트를 `application` 패키지에 인터페이스로 정의**한다 (`MailSenderPort` 패턴)
2. **어댑터를 `adapter/out`에 둔다**
3. **설정값은 `@ConfigurationProperties`로 바인딩**한다 (`AppMailProperties`, `CaptchaProperties` 패턴)
4. **미설정 시 폴백을 만든다** — 개발 환경에서 흐름이 막히지 않게
5. **타임아웃을 반드시 지정**한다
6. **실패를 삼키지 말고 최소한 로그를 남긴다** (CAPTCHA의 교훈)
7. **비밀값은 `.env`로 주입**하고 `.env.example`에 양식만 커밋한다

## 관련 문서

- [../architecture/system-context.md](../architecture/system-context.md) — 외부 시스템 관계
- [../infrastructure/environments.md](../infrastructure/environments.md) — 환경별 설정값
- [../../security/secrets-management.md](../../security/secrets-management.md) — 비밀값 관리
- [../infrastructure/runbooks/RB-001-mail-not-sent.md](../infrastructure/runbooks/RB-001-mail-not-sent.md)
