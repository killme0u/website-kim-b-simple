# UI-백엔드 정합성 및 CAPTCHA 구현 계획

## 문제와 목표

`docs/UI.md`의 인증·회원가입 화면과 현재 React·Spring Boot 구현 사이의 차이를 줄이고, 회원가입 CAPTCHA를 서버에서 재검증하도록 한다. 화면에만 존재하는 기능은 실제 백엔드 계약을 추가하거나, 제공하지 않을 기능은 UI에서 제거한다.

## 구현 범위

### 프론트엔드

- `SignupPage`: 약관 동의, 기본 정보, 이메일 인증, 아이디·닉네임 중복 확인, CAPTCHA, 가입 완료 단계
- `MyPage`: 회원 정보, 내 게시글, 내 댓글, 비밀번호 변경 진입
- `LoginPage`: 로그인 유지, 아이디 찾기·비밀번호 찾기 진입 및 백엔드 계약 연결
- `RootLayout`: 전역 검색, 홈·게시판·Q&A·자료실 네비게이션, 로그인 상태별 메뉴, 푸터
- CAPTCHA 위젯은 서버 토큰을 가입 요청에 포함하고, 토큰 원문을 전역 상태나 저장소에 보관하지 않는다.
- Google 로그인은 OAuth 계약과 운영 키가 준비되지 않은 상태에서는 가짜 성공 UI를 제공하지 않고, 기능을 제거하거나 별도 연동 단계로 남긴다.

### 백엔드

- `SignupCommand`에 `captchaToken`과 약관 동의 정보를 추가한다.
- `CaptchaVerificationPort`와 provider adapter를 추가하고, secret·site key·timeout은 환경 변수 또는 외부 설정으로 주입한다.
- 가입 트랜잭션 전에 CAPTCHA 토큰의 유효성·만료·요청 맥락을 서버에서 검증한다.
- CAPTCHA 누락·실패·만료·provider 장애는 일반화된 오류로 반환하고 회원을 생성하지 않는다.
- 이메일 인증 완료·재발송 API를 추가하고 `PENDING` 회원을 검증 후 `ACTIVE`로 전환한다.
- 비밀번호 찾기·재설정·변경 API는 임시 비밀번호 만료와 `must_change_password` 정책을 유지한다.
- 필요한 DB 변경은 별도 Flyway migration으로 작성한다.
- Google OAuth는 provider·callback·계정 연결 정책이 확정된 뒤에만 구현한다.

## API 계약

| 기능 | 계약 |
|---|---|
| 회원가입 | `POST /api/members/signup`, `captchaToken`, 약관 동의 포함 |
| 아이디 중복 | `GET /api/members/username-availability?username=` |
| 이메일 인증 완료 | `POST /api/members/verify-email` |
| 인증 메일 재발송 | `POST /api/members/verify-email/resend` |
| 현재 사용자 | `GET /api/me` |
| 내 게시글·댓글 | `GET /api/me/posts`, `GET /api/me/comments` |
| 비밀번호 찾기 | `POST /api/members/password-reset` |
| 비밀번호 변경 | 인증된 사용자 전용 API |
| CAPTCHA | 백엔드 내부 provider 검증, 토큰·secret 저장 금지 |

## 보안 및 오류 처리

- 클라이언트의 CAPTCHA 체크 상태를 신뢰하지 않고 서버 검증 결과만 사용한다.
- CAPTCHA secret은 소스와 `application.yml`에 평문으로 저장하지 않는다.
- 원본 CAPTCHA 토큰과 비밀번호는 로그·localStorage·Zustand persist에 남기지 않는다.
- 인증 토큰은 1회용·만료 처리하고, 중복 아이디·닉네임은 DB UNIQUE 제약을 최종 방어선으로 사용한다.
- 이메일 인증·비밀번호 재설정·CAPTCHA 실패 메시지는 계정 존재 여부를 과도하게 노출하지 않는다.

## 검증 기준

- CAPTCHA 토큰 누락·실패·만료·provider 장애 시 회원이 생성되지 않는다.
- CAPTCHA 성공 후에도 이메일 인증 전 회원 상태는 `PENDING`이다.
- 이메일 인증 토큰은 1회만 사용할 수 있고 만료 토큰은 거부된다.
- 중복 아이디·닉네임 가입이 거부된다.
- 회원가입·로그인·로그아웃·마이페이지·게시판·댓글 흐름이 유지된다.
- `npm run lint`, `npm run build`, 백엔드 테스트 또는 Gradle 빌드가 통과한다.

## 선결 결정

1. 운영 CAPTCHA provider와 키 주입 방식
2. 닉네임을 `member.nickname`으로 추가할지 기존 `name`을 표시명으로 사용할지
3. Google 로그인 제공 여부
4. 로그인 유지의 세션·토큰 정책
5. 아이디 찾기 본인 확인 방식
