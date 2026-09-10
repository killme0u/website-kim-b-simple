# 인수 기준

> 상태: 자동 검증 항목은 `확인됨`(테스트 파일 존재), 나머지는 `제안`입니다.
> 출처: `plan.md` 검증 기준, `PRD.md` 6·7단계 완료 조건, 그리고 실제 테스트 코드.

## 현재 자동 검증 현황 `확인됨`

테스트 클래스 5개, 테스트 17개입니다.

| 클래스 | 테스트 수 | 대상 |
|---|---|---|
| `SecurityConfigTest` | 4 | CSRF·SPA 셸 접근 |
| `MemberControllerTest` | 4 | 닉네임 중복 확인 |
| `MailConfigTest` | 3 | SMTP 폴백 |
| `SignupMailListenerTest` | 3 | 메일 발송 경로 |
| `MemberTest` | 3 | 닉네임 정규화 |

**커버되지 않는 영역**: 게시판 권한, 게시글 CRUD, 댓글, 좋아요, 조회수 중복 방지,
첨부파일, CAPTCHA, 토큰 만료·재사용.

## AC-001 회원가입

### AC-001-1 CAPTCHA 실패 시 회원이 생성되지 않는다 `제안`

| 항목 | 내용 |
|---|---|
| 조건 | `captchaToken`이 누락·오류·만료 |
| 기대 | 400, `member` 행 생성 안 됨 |
| 자동 검증 | **없음** |
| 근거 | `SignupService.java:30-32` — 트랜잭션 시작 직후 검증 |

`plan.md` 검증 기준의 첫 항목인데 테스트가 없습니다.

### AC-001-2 CAPTCHA 성공 후에도 상태는 `PENDING` `제안`

| 항목 | 내용 |
|---|---|
| 기대 | 가입 직후 `status = PENDING` |
| 자동 검증 | **없음** |
| 근거 | `Member.pending` (`member/domain/Member.java:75`) |

### AC-001-3 인증 토큰은 1회용이고 만료된다 `제안`

| 항목 | 내용 |
|---|---|
| 기대 | 두 번째 사용 시 400, 24시간 후 400 |
| 자동 검증 | **없음** |
| 근거 | `VerificationToken.isUsable` (`:54-58`) |

### AC-001-4 중복 아이디·닉네임 가입이 거부된다 `제안`

| 항목 | 내용 |
|---|---|
| 기대 | 409 `DUPLICATE` |
| 자동 검증 | **없음** (중복 확인 API는 검증되나 가입 거부는 미검증) |

### AC-001-5 닉네임 정규화가 조회와 저장에서 같다 `확인됨`

| 테스트 | 확인 내용 |
|---|---|
| `MemberTest.trimsNicknameOnCreation` | 저장 시 앞뒤 공백 제거 |
| `MemberTest.treatsBlankNicknameAsAbsent` | 공백뿐이면 `null` |
| `MemberTest.exposesNormalizationRule` | 규칙이 재사용 가능하게 공개됨 |
| `MemberControllerTest.trimsNicknameBeforeLookup` | 조회도 같은 정규화를 거침 |

결정 D5의 회귀 방지 테스트입니다. `5cca82f`에서 추가되었습니다.

### AC-001-6 중복 확인 결과가 각 입력란 아래에 표시된다 `제안`

| 항목 | 내용 |
|---|---|
| 기대 | 아이디·닉네임 양쪽 모두 결과 문구 표시 |
| 자동 검증 | **없음** — 프론트엔드 테스트 없음 |
| 근거 | `5cca82f`에서 고친 버그. 회귀 위험 있음 |

프론트엔드에 테스트 러너 설정이 없습니다(`package.json`에 `test` 스크립트 없음).

### AC-001-7 빈 닉네임은 available `확인됨`

`MemberControllerTest.treatsBlankNicknameAsAvailable`

닉네임은 선택 항목이라 정규화 결과가 `null`이면 충돌 대상이 없습니다.

## AC-002 인증·세션

### AC-002-1 모든 응답에 XSRF-TOKEN 쿠키가 내려간다 `확인됨`

`SecurityConfigTest.writesCsrfCookieOnEveryResponse`

SPA가 토큰을 읽을 수 있어야 로그아웃이 가능합니다.

### AC-002-2 CSRF 토큰 없는 로그아웃은 403 `확인됨`

`SecurityConfigTest.logoutWithoutCsrfTokenIsForbidden`

### AC-002-3 쿠키 원본 토큰을 헤더로 보내면 로그아웃 성공 `확인됨`

`SecurityConfigTest.logoutWithCookieCsrfTokenSucceeds`

이 셋이 함께 `csrf().spa()` 채택 결정을 고정합니다.
기본 `XorCsrfTokenRequestAttributeHandler`로 되돌리면 AC-002-3이 깨집니다.

### AC-002-4 비로그인 상태에서 SPA 셸이 접근 가능하다 `확인됨`

`SecurityConfigTest.spaShellIsReachableAnonymously`

정적 리소스가 401이 되면 로그인 화면 자체를 볼 수 없게 됩니다.

### AC-002-5 `SUSPENDED`·`DELETED` 계정은 로그인할 수 없다 `제안`

| 항목 | 내용 |
|---|---|
| 자동 검증 | **없음** |
| 근거 | `CustomUserDetails.java:47-49, 57-59` |

전환 코드가 없어 실제로 도달하지 않는 상태지만, 판정 로직은 존재합니다.

## AC-003 메일 발송

### AC-003-1 SMTP 미설정 시 로그 전용으로 폴백한다 `확인됨`

| 테스트 | 조건 |
|---|---|
| `MailConfigTest.fallsBackWhenHostIsBlank` | `.env` 없이 기본값만 |
| `MailConfigTest.fallsBackWhenCredentialsAreBlank` | 호스트만 있고 계정이 빈 상태 |

두 번째가 중요합니다 — `.env.example`을 복사만 한 직후 상태에서
기동은 되고 가입 시점에 SMTP 인증 실패로 터지는 것을 막습니다.

### AC-003-2 완전 설정 시 실제 SMTP를 쓴다 `확인됨`

`MailConfigTest.usesSmtpWhenFullyConfigured`

### AC-003-3 가입 이벤트가 인증 링크가 담긴 메일을 보낸다 `확인됨`

`SignupMailListenerTest.sendsSignupVerificationMail`

### AC-003-4 재설정·아이디찾기도 같은 발송 경로를 탄다 `확인됨`

`SignupMailListenerTest.sendsAccountRecoveryMails`

### AC-003-5 Thymeleaf 템플릿 엔진이 등록된다 `확인됨`

`SignupMailListenerTest.templateEngineIsAvailable`

### AC-003-6 실제 메일이 수신된다 `미결정`

| 항목 | 내용 |
|---|---|
| 상태 | **미검증** — `todo.md`에 "발송된 메일이 없음" |
| 자동 검증 | 불가능 (외부 SMTP 의존) |
| 수동 절차 | [../technology/infrastructure/runbooks/RB-001-mail-not-sent.md](../technology/infrastructure/runbooks/RB-001-mail-not-sent.md) |

AC-003-1~5가 모두 통과해도 실제 메일은 안 갈 수 있습니다.
테스트는 `MailSenderPort` 수준까지만 확인하고 SMTP 대화는 검증하지 않습니다.

## AC-004 게시판 권한 `제안`

모두 자동 검증이 없습니다.

| ID | 기준 | 근거 |
|---|---|---|
| AC-004-1 | 비회원이 Q&A·자료실 목록 조회 시 401 | `Board.checkReadable` |
| AC-004-2 | 비회원이 자유게시판은 읽고 쓸 수 있다 | `V2` 시드 |
| AC-004-3 | 댓글 불가 게시판에 댓글 작성 시 400 | `CommentService.java:49-51` |
| AC-004-4 | 게시판 목록은 비로그인도 조회 가능 | `BoardService.java:26-31` |

## AC-005 게시글 권한 `제안`

| ID | 기준 | 근거 |
|---|---|---|
| AC-005-1 | 비회원 글은 올바른 비밀번호로만 수정된다 | `Post.java:85-87` |
| AC-005-2 | 틀린 비밀번호는 403 | 동일 |
| AC-005-3 | 회원 글은 비로그인으로 수정할 수 없다 | `Post.java:82-84` |
| AC-005-4 | 다른 회원의 글을 수정하면 403 | `Post.java:80` |
| AC-005-5 | 관리자는 모든 글을 수정할 수 있다 | `Post.java:78` |
| AC-005-6 | 삭제된 글은 조회되지 않는다 | `deleted_at` 필터 |

## AC-006 조회수·좋아요 `제안`

`PRD.md` 6단계 완료 조건(D2 검증)에서 왔습니다.

| ID | 기준 | 상태 |
|---|---|---|
| AC-006-1 | 같은 회원이 같은 날 같은 글을 두 번 봐도 조회수가 1만 오른다 | 구현됨, 미검증 |
| AC-006-2 | 날짜가 바뀌면 다시 1 오른다 | 구현됨, 미검증 |
| AC-006-3 | 좋아요는 회원당 1회만 반영된다 | 구현됨, 미검증 |
| AC-006-4 | 좋아요 취소 시 `like_count`가 음수가 되지 않는다 | 구현됨, 미검증 |
| AC-006-5 | **비로그인 조회수가 중복 방지된다** | **미구현** `미결정` |

AC-006-5는 `PRD.md` 2.6이 설계했으나 구현되지 않았습니다.
현재는 새로고침마다 증가합니다.

## AC-007 첨부파일 `제안`

`PRD.md` 7단계 완료 조건(D4 검증)에서 왔습니다.

| ID | 기준 | 상태 |
|---|---|---|
| AC-007-1 | 경로 탈출 시도가 차단된다 | 구현됨(500으로 응답) `미결정` |
| AC-007-2 | SVG는 `IMAGE`로 분류되지 않는다 | 구현됨, 미검증 |
| AC-007-3 | 저장 파일명이 추측 불가능하다 | 구현됨(UUID) |
| AC-007-4 | 100MB 초과 업로드가 거부된다 | 구현됨, 미검증 |
| AC-007-5 | **내용 기반으로 MIME을 판정한다** | **미구현** `미결정` |
| AC-007-6 | **첨부 불가 게시판에 첨부할 수 없다** | **미구현** `미결정` |

## AC-008 설정·배포 `제안`

`plan.md` 검증 기준에서 왔습니다.

| ID | 기준 | 확인 방법 |
|---|---|---|
| AC-008-1 | `.env` 없이도 앱이 기동한다 | `optional:file:` 설정 |
| AC-008-2 | `.env`의 SMTP 값을 채우면 실제 발송으로 전환된다 | `MailConfigTest` 간접 검증 |
| AC-008-3 | `PGSQL_HOST`·`PGSQL_PORT`만 바꾸면 대상 DB가 바뀐다 | 수동 |
| AC-008-4 | `git check-ignore -v .env`가 무시 대상으로 보고한다 | 수동 |
| AC-008-5 | Flyway 검증 실패 시 스키마가 삭제되지 않고 기동이 실패한다 | `clean-disabled: true` |
| AC-008-6 | `docker compose up -d --build`가 성공한다 | 수동 |
| AC-008-7 | 컨테이너가 `/`, `/favicon.svg`, `/api/boards`에 200을 응답한다 | 수동 |
| AC-008-8 | `npm run lint`, `npm run build`가 통과한다 | 수동 |

AC-008은 전부 수동입니다. CI가 없어 자동으로 돌지 않습니다.
→ [../technology/infrastructure/ci-cd.md](../technology/infrastructure/ci-cd.md)

## 우선 보강 제안 `제안`

위험 대비 비용이 낮은 순입니다.

| 순위 | 대상 | 이유 |
|---|---|---|
| 1 | AC-005 게시글 권한 | 보안 직결. 도메인 단위 테스트라 작성 비용이 낮음 |
| 2 | AC-006-1~2 조회수 | 결정 D2의 핵심. `@DataJpaTest`로 검증 가능 |
| 3 | AC-001-1 CAPTCHA 실패 | 스팸 방어의 유일한 관문 |
| 4 | AC-001-3 토큰 만료·재사용 | 계정 탈취와 직결 |
| 5 | AC-004 게시판 권한 | `V4` 결정이 회귀하지 않도록 |

1과 2는 외부 의존 없이 순수 도메인·JPA 테스트로 쓸 수 있어 가장 저렴합니다.

## 관련 문서

- [../technology/testing-strategy.md](../technology/testing-strategy.md) — 테스트 전략
- [../security/security-test-plan.md](../security/security-test-plan.md) — 보안 테스트
- [../business/business-requirements.md](../business/business-requirements.md) — 요구사항 대응
