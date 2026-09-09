# 할 일

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
