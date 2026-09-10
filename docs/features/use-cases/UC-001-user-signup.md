# UC-001 회원가입

> 상태: `확인됨` — 흐름과 분기가 코드에 존재합니다.
> 관련: [../specifications/FR-001-authentication.md](../specifications/FR-001-authentication.md),
> [../user-journeys.md](../user-journeys.md) J-002

## 개요

| 항목 | 값 |
|---|---|
| 주 행위자 | 비회원 방문자 |
| 목적 | 회원 계정을 만들고 이메일 인증으로 활성화한다 |
| 범위 | 가입 요청 → 인증 메일 발송 → 인증 완료 |
| 진입점 | `POST /api/members/signup`, `GET /api/members/verify-email` |
| 화면 | `frontend-react/src/pages/SignupPage.tsx`, `VerifyEmailPage.tsx` |

## 사전 조건

| 조건 | 검증 여부 |
|---|---|
| 로그인 상태가 아니어야 함 | **검증하지 않음** — 로그인 상태에서도 가입 가능 `미결정` |
| 아이디가 미사용이어야 함 | DB UNIQUE (최종), 중복 확인 API (편의) |
| 이메일이 미사용이어야 함 | DB UNIQUE (최종), **사전 확인 API 없음** |
| 닉네임이 미사용이어야 함 | 부분 UNIQUE (최종), 중복 확인 API (편의) |
| CAPTCHA 토큰 보유 | 서버 검증 |

**이메일 중복만 사전 확인 수단이 없습니다.** 아이디·닉네임은 확인 API가 있는데
이메일은 제출해야만 알 수 있고, 그때 409 `DUPLICATE`가 납니다. `미결정`

## 사후 조건

성공 시:

| 대상 | 상태 |
|---|---|
| `member` 행 | 생성됨, `status = PENDING`, `role = USER` |
| `verification_token` 행 | 생성됨, `purpose = EMAIL_VERIFICATION`, 24시간 유효 |
| 인증 메일 | 커밋 후 비동기 발송 (**성공 보장 없음**) |
| 응답 | 201 + `{ "id": <회원ID> }` |

## 기본 흐름

### 1단계 — 약관 동의

화면에서 동의를 받고 `termsAccepted`를 담아 보냅니다.

**서버는 이 값을 검증하지도 저장하지도 않습니다.** `미결정`
`SignupCommand.termsAccepted`에 `@NotNull`이 없고 `SignupService`가 읽지 않습니다
(`member/adapter/in/web/dto/SignupCommand.java:37`).
동의 기록이 남지 않아 나중에 증빙할 수 없습니다.

### 2단계 — 기본 정보 입력

| 필드 | 제약 | 필수 |
|---|---|---|
| `username` | 4~30자 | 예 |
| `password` | 8~100자, **복잡도 요구 없음** | 예 |
| `name` | ~50자 | 예 |
| `nickname` | ~30자 | 아니오 |
| `email` | 이메일 형식 | 예 |
| `phone` | ~20자 | **예** (UI.md는 선택으로 그림) `미결정` |

### 3단계 — 중복 확인

```
GET /api/members/username-availability?username=hong
    → { "available": true }        원본 그대로 조회

GET /api/members/nickname-availability?nickname=%20홍길동
    → normalizeNickname(" 홍길동") = "홍길동" 으로 조회
    → { "available": false }       공백만 다른 값도 중복으로 판정
```

화면은 두 결과를 각 입력란 아래에 같은 형태로 표시하고, 값이 비었거나 요청 중이면
`중복 확인` 버튼을 비활성화합니다(`plan.md`, `UI.md` 아이디·닉네임 중복 확인 규칙).

이 표시가 닉네임 쪽에만 빠져 있던 것이 `5cca82f`에서 고친 버그입니다.

### 4단계 — CAPTCHA

`CaptchaField.tsx`가 토큰을 받아 요청에 싣습니다.
**토큰 원문은 전역 상태나 저장소에 보관하지 않습니다**(`plan.md` 보안 항목).

### 5단계 — 제출

`POST /api/members/signup` (`member/application/SignupService.java:28-47`)

```
1. captchaVerifier.verify(captchaToken, remoteAddr)
      실패 → IllegalArgumentException → 400
             ※ 회원이 생성되지 않음 (트랜잭션 시작 직후 검증)

2. Member.pending(...)
      nickname은 normalizeNickname으로 정규화
      status = PENDING, role = USER
      password는 DelegatingPasswordEncoder로 해시

3. memberRepository.save(member)
      UNIQUE 위반 시 → 409 DUPLICATE

4. rawToken = UUID.randomUUID()
   VerificationToken.emailVerify(member, sha256(rawToken), 24시간)
      DB에는 해시만 저장

5. events.publishEvent(new SignupCompleted(email, rawToken))

6. return 201 { "id": ... }

--- 트랜잭션 커밋 ---

7. SignupMailListener.onSignupCompleted  (@Async, AFTER_COMMIT)
      Thymeleaf mail/email-verification 렌더링
      링크: {APP_BASE_URL}/verify-email?token={rawToken}
      실패 시 log.error만 남기고 끝
```

### 6단계 — 이메일 인증

```
GET /api/members/verify-email?token=xxx
    → sha256(token)으로 EMAIL_VERIFICATION 토큰 조회
        없음 → 400 "유효하지 않은 이메일 인증 토큰입니다."
    → isUsable("EMAIL_VERIFICATION")
        purpose 불일치 / 이미 사용 / 만료 → 400
    → member.verifyEmail()    PENDING → ACTIVE
    → token.markUsed()
    → 200 { "status": "verified" }
```

## 대체 흐름

### A1. CAPTCHA 실패

400 `BAD_REQUEST` + "CAPTCHA 검증에 실패했습니다."
회원은 생성되지 않습니다. 사용자는 CAPTCHA를 다시 풀고 재시도합니다.

`mode=fake`에서는 `dev-captcha`와 정확히 일치해야 통과합니다.
`mode=remote`에서 provider가 타임아웃·오류를 내면 **모두 실패로 처리**됩니다
(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:60-67`).
즉 provider 장애 = 가입 전면 중단입니다.

### A2. 아이디·이메일·닉네임 중복

409 `DUPLICATE` + "이미 사용 중인 값이거나 중복된 데이터입니다."

**어느 필드가 중복인지 알려주지 않습니다.** 세 UNIQUE 제약이 같은 메시지를 냅니다. `미결정`

### A3. 인증 메일이 오지 않음

사용자 관점에서는 201을 받고 기다리기만 합니다. 실패를 알 수 없습니다.

원인 후보:
- SMTP 미설정 → `LoggingMailSender`로 폴백(로그에만 남음)
- SMTP 설정 오류 → `log.error`만 남음
- 메일이 스팸함으로

대응: 재발송 API (`POST /api/members/verify-email/resend`)
진단: [../../technology/infrastructure/runbooks/RB-001-mail-not-sent.md](../../technology/infrastructure/runbooks/RB-001-mail-not-sent.md)

`todo.md`에 "발송된 메일이 없음"이 열려 있는 상태입니다.

### A4. 인증 링크 만료 (24시간 초과)

400 + "만료되었거나 이미 사용된 이메일 인증 토큰입니다."
재발송으로 새 토큰을 받습니다. **이전 토큰은 무효화되지 않고 그대로 남습니다.** `미결정`

### A5. 인증 링크 재사용

`used_at`이 설정되어 있으므로 A4와 같은 400이 납니다.

## 예외 흐름

### E1. 메일 클라이언트의 링크 프리페치 `미결정`

`GET /api/members/verify-email`은 GET이면서 상태를 바꿉니다.
일부 메일 클라이언트·보안 게이트웨이가 링크를 미리 열어보면 토큰이 소비되고,
사용자가 실제로 클릭했을 때는 이미 사용된 토큰이 됩니다.

`plan.md`의 API 계약표는 `POST /api/members/verify-email`로 적혀 있어
구현과 다릅니다. 어느 쪽이 정본인지 결정이 필요합니다.

### E2. 재발송 남용 `미결정`

`POST /api/members/verify-email/resend`에 횟수 제한이 없습니다.
호출할 때마다 `verification_token` 행이 쌓이고, 모두 유효한 상태로 공존합니다.
같은 주소로 메일을 반복 발송하게 만들 수 있습니다(메일 폭탄).

## 설계상 미해결 `미결정`

### 가입 흐름의 정본이 둘

| 출처 | 흐름 |
|---|---|
| `UI.md` | **가입 요청 전에** 이메일 인증을 마침 |
| 구현 (`SignupPage` 3단계) | 가입 요청 후 `PENDING` 상태에서 인증 |

`todo.md`에 "어느 쪽을 정본으로 삼을지 결정 필요"로 남아 있습니다.

### 인증이 실제로 막는 것이 없음

`PENDING`으로도 로그인이 되고(`CustomUserDetails.isEnabled()`가 `PENDING`을 허용),
게시판 접근은 로그인 여부만 봅니다(`Board.checkReadable`).
따라서 이메일 인증을 하지 않아도 Q&A·자료실을 이용할 수 있습니다.

인증 단계가 현재 상태에서는 형식적입니다.

## 검증 `확인됨`

| 항목 | 테스트 |
|---|---|
| 닉네임 앞뒤 공백 정규화 | `MemberTest.trimsNicknameOnCreation` |
| 공백뿐인 닉네임 → null | `MemberTest.treatsBlankNicknameAsAbsent` |
| 정규화 규칙 공개 | `MemberTest.exposesNormalizationRule` |
| 중복 닉네임 판정 | `MemberControllerTest.reportsTakenNickname` |
| 공백만 다른 닉네임 중복 판정 | `MemberControllerTest.trimsNicknameBeforeLookup` |
| 빈 닉네임은 available | `MemberControllerTest.treatsBlankNicknameAsAvailable` |
| 미사용 닉네임은 available | `MemberControllerTest.reportsFreeNickname` |
| 가입 시 인증 메일 발송 | `SignupMailListenerTest.sendsSignupVerificationMail` |

**테스트가 없는 것**: CAPTCHA 검증 분기, 토큰 만료·재사용 거부,
`PENDING` → `ACTIVE` 전이, 아이디·이메일 중복 시 409.

## 관련 문서

- [../specifications/FR-001-authentication.md](../specifications/FR-001-authentication.md) — 기능 명세
- [../state-machines.md](../state-machines.md) — SM-001, SM-002
- [../acceptance-criteria.md](../acceptance-criteria.md) — AC-001
- [../../security/authentication-authorization.md](../../security/authentication-authorization.md)
