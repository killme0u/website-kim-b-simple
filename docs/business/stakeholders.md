# 이해관계자

> 상태: 대부분 `제안`입니다. 저장소에는 조직 정보가 없고, 확인 가능한 것은
> git 커밋 작성자와 `.env.example`의 발신 주소뿐입니다.

## 저장소에서 확인되는 사실 `확인됨`

| 항목 | 값 | 근거 |
|---|---|---|
| 커밋 작성자 | Kim Baek | git log |
| 메일 발신 주소 설정 | `MAIL_FROM_ADMIN` | `.env.example` |
| 애플리케이션 식별자 | `board-backend` | `application.yml` `spring.application.name` |
| Java 패키지 | `page.sanotehu.board.backend` | 소스 트리 |
| Gradle group | `com.edu` | `backend-springboot/build.gradle` |

`page.sanotehu`(도메인 역순)와 `com.edu`(Gradle group)가 서로 다릅니다.
학습·교육 목적으로 시작해 개인 도메인으로 옮긴 흔적으로 보이나, 확인된 사실은 아닙니다. `제안`

## 역할 정의 `제안`

현재는 1인 개발 프로젝트로 보입니다. 아래는 인원이 늘 때를 대비한 역할 구분안이며,
채택 전까지 구속력이 없습니다.

| 역할 | 책임 | 결정 권한 | 현재 담당 |
|---|---|---|---|
| 제품 책임자 | 요구사항 우선순위, `미결정` 항목 확정 | `PRD.md` 10장 잔여 결정 | 미지정 |
| 백엔드 개발 | 도메인·API·마이그레이션 | 스키마 변경, API 계약 | 미지정 |
| 프론트엔드 개발 | SPA 화면, UI 계약 준수 | 컴포넌트 구성 | 미지정 |
| 운영 | 배포, SMTP·CAPTCHA 키 관리 | 환경 변수 값 | 미지정 |
| 보안 검토 | 위협 모델 갱신, 취약점 대응 | 릴리스 차단 | 미지정 |

## 시스템 사용자 `확인됨`

코드가 실제로 구분하는 주체는 셋입니다.

### 1. 익명 사용자 (비회원)

- 세션 없음. `@AuthenticationPrincipal CustomUserDetails`가 `null`
- 자유게시판 읽기·쓰기 가능
- 글 작성 시 `guestNickname` + `guestPassword` 제공 (`post/domain/Post.java:56-64`)
- 수정·삭제 시 같은 비밀번호로 본인 확인 (`Post.java:85-87`)
- 댓글·좋아요 불가

### 2. 회원 (`MemberRole.USER`)

- `PENDING` 상태에서는 계정이 존재하지만 이메일 인증 전
- `ACTIVE`가 되어야 Q&A·자료실 이용 가능
- 자기 글·댓글만 수정·삭제
- 좋아요 가능, 조회수는 하루 1회만 반영

### 3. 관리자 (`MemberRole.ADMIN`)

- 모든 글·댓글 수정·삭제 (`Post.java:78`, `comment/application/CommentService.java:64, 76`)
- **관리자 계정 생성 경로가 없습니다.** `SignupService`는 항상 `USER`로 만듭니다
  (`member/domain/Member.java:76`). DB에서 직접 `role`을 바꾸는 것 외에 방법이 없습니다. `미결정`

## 외부 의존 주체 `확인됨`

| 주체 | 관계 | 실패 시 영향 |
|---|---|---|
| SMTP 제공자 (네이버 메일 기본값) | 인증·비밀번호 재설정·아이디 찾기 메일 발송 | 가입은 성공하나 인증 메일 미도달 → 계정이 `PENDING`에 머묾 |
| CAPTCHA 제공자 | 가입·비밀번호 재설정 요청 검증 | 검증 실패로 처리되어 가입 차단 (`ConfiguredCaptchaVerifier`가 예외를 `false`로 흡수) |
| PostgreSQL 15 | 모든 영속 데이터 | 기동 불가 |

SMTP와 CAPTCHA는 모두 **미설정 시 폴백**이 있습니다.
SMTP는 로그 전용 발송기로(`config/MailConfig.java:39-45`),
CAPTCHA는 `mode=fake`로 고정 토큰만 통과시킵니다(`ConfiguredCaptchaVerifier.java:29-31`).
둘 다 개발 편의용이며 운영에서 쓰면 안 됩니다.

## 결정이 필요한 사람 `미결정`

`PRD.md` 10장과 `plan.md`의 선결 결정 목록에 있는 항목들은 담당자가 지정되지 않았습니다.

| 결정 사항 | 출처 |
|---|---|
| 운영 CAPTCHA provider와 키 주입 방식 | `plan.md` 선결 결정 #1 |
| Google 로그인 제공 여부 | `plan.md` #3 |
| 로그인 유지의 세션·토큰 정책 | `plan.md` #4 |
| 아이디 찾기 본인 확인 방식 | `plan.md` #5 |
| 가입 흐름 정본 (가입 전 인증 vs 가입 후 `PENDING` 인증) | `todo.md` 후속 검토 |

## 관련 문서

- [../security/access-control.md](../security/access-control.md) — 주체별 권한 매트릭스
- [../security/incident-response.md](../security/incident-response.md) — 대응 역할
- [roadmap.md](roadmap.md) — 결정 대기 항목의 일정
