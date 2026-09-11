# 할 일

## D2026-09-11 10:06

### 미구현


### 검토

- ~~회원가입 폼에서 아이디/닉네임이 이미 사용 중이어도 "중복 확인을 완료해 주세요" 에러~~ → **완료 (2026-09-11)**
  - 증상: 화면에 빨간색으로 "이미 사용 중입니다" 표시되지만, 제출하면 "중복 확인을 완료해 주세요" 에러 발생.
  - 원인: 검증 로직이 `usernameAvailable !== true` 체크만 했는데, 이건 false도 막는다.
    따라서 사용자가 이미 사용 중인 아이디임을 알고도 제출하면 "완료해 주세요"라는 엉뚱한 에러를 받는다.
  - 조치: 검증 순서를 변경 — false인지 먼저 체크해 "이미 사용 중인 아이디입니다" 메시지를 주고,
    그 다음 true인지 체크해 "완료해 주세요" 메시지를 줌. 닉네임도 동일 패턴으로 수정.
  - 파일: `frontend-react/src/pages/SignupPage.tsx:68-76` (submit 함수 검증 로직).

## D2026-09-11 9:52

### 미구현


### 검토

- ~~회원가입 버튼을 누르면 "CAPTCHA 검증에 실패했습니다."로 실패~~ → **완료 (2026-09-11)**
  - 증상: 위젯은 "성공!"인데 가입만 실패. 아이디 찾기·비밀번호 재설정·인증메일 재발송도 같은 원인.
  - 원인: 프런트와 백엔드가 서로 다른 CAPTCHA 방식이었다. `CaptchaField.tsx`는 언제나 Turnstile
    위젯 토큰을 보내는데(fake 경로가 없다), `.env`는 `CAPTCHA_MODE=fake`라 `ConfiguredCaptchaVerifier`가
    `token.equals("dev-captcha")`로 판정해 항상 false였다. 위젯 성공과 서버 거절은 정확히 일관된 결과.
  - 언제 깨졌나: 커밋 `d921647`에서 체크박스 필드(`dev-captcha`를 그대로 내보내 fake와 짝이 맞았다)를
    실제 Turnstile 위젯으로 교체하면서 `.env`를 fake로 남겨 둔 것.
  - 조치: `.env`·`.env.example`을 `mode=remote` + Cloudflare siteverify 엔드포인트 +
    공개 테스트 secret(`1x00...0AA`)으로 전환. 프런트는 `VITE_TURNSTILE_SITE_KEY` 미설정 시
    짝이 되는 테스트 site key(`1x00000000000000000000AA`)로 폴백하므로 둘이 맞는다.
  - 함께 고친 것 — `docker-compose.yml`에 `CAPTCHA_*` passthrough 추가. `.dockerignore`가 `.env`를
    이미지에서 빼고 compose도 `CAPTCHA_*`를 넘기지 않아서, `.env`만 고치면 컨테이너 배포에는
    반영되지 않고 `application.yml`의 기본값 `fake`로 되돌아가 같은 버그가 남는 상태였다.
  - 회귀 테스트: `ConfiguredCaptchaVerifierTest` 신규 6개. 그동안 CAPTCHA 테스트가 전부
    `@MockitoBean CaptchaVerifier`로 목 처리를 해서 정작 판정 구현체는 한 번도 실행되지 않았다.
  - 남은 것: `application.yml:78` 기본값이 아직 `${CAPTCHA_MODE:fake}`라 `.env`·compose 환경변수가
    모두 없는 환경은 같은 버그를 재현한다. 운영 전에는 Cloudflare 실제 키로 교체 필요.

## D2026-09-10 5:54

### 미구현


### 검토

- 이메일 인증 기능의 정상 동작 확인 필요
- 발송된 메일이 없음

## D2026-09-10 5:12

### 미구현


### 검토

- ~~회원가입 화면에서 닉네임 중복 확인~~ → **완료 (2026-09-10)**
  - 원인: API·백엔드는 정상. `SignupPage.tsx`의 닉네임 `Field`에만 판정 결과 표시 블록이 없었다.
    `nicknameAvailable` 상태를 저장하고 제출 검증에도 쓰면서 화면에는 렌더링하지 않아,
    "닉네임 중복 확인을 완료해 주세요."로 가입이 막혀도 사용자가 이유를 알 수 없었다.
  - 조치: 아이디 필드와 같은 형태로 결과 문구를 표시. 값이 비었거나 요청 중이면 두 `중복 확인` 버튼을 비활성화.
  - 함께 고친 것 — 중복 확인은 원본 값으로 조회하는데 저장은 `trim` 후 저장해서
    `" 단팥빵"`이 "사용 가능"으로 보인 뒤 가입에서 409가 나던 문제.
    정규화 규칙을 `Member.normalizeNickname` 한 곳으로 모으고 조회·저장이 공유하도록 함(PRD D5).
  - 회귀 테스트: `MemberControllerTest`, `MemberTest`. 문서: PRD 2.4·5.5, `UI.md` 「아이디·닉네임 중복 확인 규칙」, `plan.md`.

<details>
<summary>원래 보고 내용</summary>

요청을 다음과 같이 실행

```js
await fetch("http://localhost:5173/api/members/nickname-availability?nickname=%EC%82%B4%EC%95%84%EC%95%BC%EB%8F%BC", {
    "credentials": "include",
    "headers": {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:155.0) Gecko/20100101 Firefox/155.0",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "X-XSRF-TOKEN": "5dc26c25-e386-44c5-af58-cbf2ca190c51",
        "Sec-Fetch-Dest": "empty",
        "Sec-Fetch-Mode": "cors",
        "Sec-Fetch-Site": "same-origin",
        "Priority": "u=0"
    },
    "referrer": "http://localhost:5173/signup",
    "method": "GET",
    "mode": "cors"
});
```

응답이 다음과 같이 반환되는데 회원가입 화면에서는 아이디 중복 화면처럼 결과을 알 수 있게 표시해 주지 못함

```json
{"available":false}
```

</details>

## D2026-09-09 1:30

### 미구현

- ~~로그인 하지 않아도 Q&A, 자료실 입장 가능~~ → **완료 (2026-09-09)**
  - PRD 1.2의 "회원제 게시판" 정의를 따라 Q&A·자료실은 읽기에도 로그인을 요구하도록 전환
    (`V4__member_only_board_read.sql`, PRD 10장 미결정 #1을 B안으로 결정).
  - 자유게시판(`free`)은 비회원제 유지.
  - 되돌리려면 `requires_auth_to_read`를 `false`로 바꾸는 마이그레이션 한 줄이면 됨.
- ~~회원가입 활성화 메일 전송 기능~~ → **완료 (2026-09-09)**
  - `MailSenderPort` + SMTP/로그 어댑터, Thymeleaf HTML 메일 템플릿 3종.
  - 인증 링크 `/verify-email?token=...`을 받는 SPA 페이지 추가.
  - `spring.mail.host`가 없으면 로그로만 남기는 개발용 폴백으로 동작.

### 오류

- ~~로그아웃~~ → **완료 (2026-09-09)**
  - 원인: `CookieCsrfTokenRepository` + 기본 `XorCsrfTokenRequestAttributeHandler` 조합이라
    XSRF-TOKEN 쿠키가 실제로 내려가지 않았고, 쿠키 원본 값을 헤더로 보내면 토큰이 어긋나 403이 발생.
  - 조치: Spring Security 7의 `csrf().spa()` 사용. 회귀 테스트는 `SecurityConfigTest`.

## 후속 검토 대상

- `SecurityConfig`의 게시글 쓰기·수정·삭제는 여전히 `permitAll`이며, 실제 권한은 `Board`/`Post` 도메인이 판정한다.
  비회원 글쓰기가 필요 없는 게시판이 늘어나면 HTTP 레이어로 끌어올릴지 재검토 필요.
- 비밀번호 재설정은 API와 화면이 모두 있으나 임시 비밀번호(`temp_password_expires_at`) 정책은 미사용.
- `docs/UI.md`의 가입 흐름은 **가입 요청 전** 이메일 인증을 마치는 설계인데, 구현은 가입 요청 후
  `PENDING` 상태에서 인증한다(`SignupPage` 3단계). 어느 쪽을 정본으로 삼을지 결정 필요.
  (2026-09-10 닉네임 검토 중 발견. 설계 결정 사안이라 이번 수정에서는 건드리지 않음.)
- `docs/UI.md`는 휴대전화 번호를 선택 항목으로 그리지만 `SignupPage`는 `required`다. 둘 중 하나를 맞춰야 한다.
- 아이디(`username`)는 저장 시 `trim`하지 않는다. 중복 확인도 원본으로 조회하므로 현재는 일치하지만,
  앞뒤 공백이 든 아이디가 만들어질 수 있다. 닉네임처럼 정규화할지 검토 필요(PRD D5 참고).
