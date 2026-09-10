# 용어 사전

> 상태: `확인됨` — 모든 항목이 코드 식별자 또는 DB 컬럼에서 왔습니다.

코드는 영어, 문서와 화면은 한국어입니다. 그 대응을 여기서 고정합니다.

## 주체

| 한국어 | 코드 식별자 | 정의 |
|---|---|---|
| 회원 | `Member` | 가입한 사용자. `member` 테이블의 행 |
| 비회원 / 게스트 | (엔티티 없음) | 세션이 없는 방문자. 글에는 `guest_nickname`으로만 남음 |
| 관리자 | `MemberRole.ADMIN` | 모든 글·댓글을 수정·삭제할 수 있는 회원 |
| 행위자 | `actor` | 권한 판정 시점의 주체. `Optional<Member>`이며 비어 있으면 비회원 |

`actor`라는 이름은 `Board.checkReadable(Optional<Member> actor)`처럼 도메인 메서드 인자에 쓰입니다.
`Optional`이 비어 있는 것이 곧 비회원이라는 뜻입니다. (`board/domain/Board.java:43`)

## 계정 상태

| 한국어 | 코드 | 뜻 |
|---|---|---|
| 인증 대기 | `MemberStatus.PENDING` | 가입은 됐으나 이메일 인증 전. 가입 직후 기본값 |
| 활성 | `MemberStatus.ACTIVE` | 이메일 인증 완료 |
| 정지 | `MemberStatus.SUSPENDED` | 정의만 존재. **전환 코드 없음** `미결정` |
| 탈퇴 | `MemberStatus.DELETED` | 정의만 존재. **전환 코드 없음**. 아이디 찾기·비밀번호 재설정에서 제외 대상으로만 쓰임 |

(`member/domain/MemberStatus.java`, 전환은 [../features/state-machines.md](../features/state-machines.md) 참조)

## 게시판

| 한국어 | 코드 / 컬럼 | 뜻 |
|---|---|---|
| 게시판 | `Board` / `board` | 글을 담는 공간이자 **정책의 보유자** |
| 게시판 식별자 | `slug` | URL에 쓰이는 짧은 이름. `free`, `qna`, `archive` |
| 읽기 인증 요구 | `requiresAuthToRead` / `requires_auth_to_read` | 참이면 목록·본문 열람에 로그인 필요 |
| 쓰기 인증 요구 | `requiresAuthToWrite` / `requires_auth_to_write` | 참이면 글 작성에 로그인 필요 |
| 댓글 허용 | `allowsComment` / `allows_comment` | 거짓이면 댓글 작성 시 400 |
| 첨부 허용 | `allowsAttachment` / `allows_attachment` | 프론트엔드 UI 판단용. 백엔드 미검증 `미결정` |
| 노출 순서 | `displayOrder` / `display_order` | 게시판 목록 정렬 키 (오름차순) |

## 게시글·댓글

| 한국어 | 코드 / 컬럼 | 뜻 |
|---|---|---|
| 게시글 | `Post` / `post` | 회원 글 또는 비회원 글. 둘은 배타적 |
| 비회원 닉네임 | `guestNickname` / `guest_nickname` | 비회원 글의 표시 이름 |
| 비회원 비밀번호 | `guestPasswordHash` / `guest_password_hash` | 비회원 글 수정·삭제 시 본인 확인용 해시 |
| 소프트 삭제 | `deletedAt` / `deleted_at` | 값이 있으면 삭제된 것. 행은 남음 |
| 조회수 | `viewCount` / `view_count` | 중복 방지를 거친 누적 조회 수 |
| 좋아요 수 | `likeCount` / `like_count` | `post_like` 행 수의 캐시 |
| 댓글 | `Comment` / `comment` | 회원만 작성 가능 |

### 회원 글과 비회원 글의 배타 관계 `확인됨`

`post_author_ck` CHECK 제약이 강제합니다 (`V1__init.sql`).

- 회원 글: `member_id`가 있고 `guest_nickname`·`guest_password_hash`가 `NULL`
- 비회원 글: `member_id`가 `NULL`이고 나머지 둘이 있음

둘 다 있거나 둘 다 없는 상태는 DB가 거부합니다.

## 인증·토큰

| 한국어 | 코드 / 컬럼 | 뜻 |
|---|---|---|
| 인증 토큰 | `VerificationToken` / `verification_token` | 이메일 인증·비밀번호 재설정용 1회용 토큰 |
| 토큰 용도 | `purpose` | `EMAIL_VERIFICATION` 또는 `PASSWORD_RESET` |
| 토큰 해시 | `tokenHash` / `token_hash` | SHA-256. **원문은 저장하지 않음** |
| 사용 시각 | `usedAt` / `used_at` | 값이 있으면 이미 쓴 토큰 |
| 원문 토큰 | `rawToken` | 메일 링크에만 실리는 UUID. DB·로그에 남지 않음 |
| 비밀번호 강제 변경 | `mustChangePassword` / `must_change_password` | 임시 비밀번호 정책용. 현재 미사용 `미결정` |

토큰 원문은 `UUID.randomUUID()`로 만들어 메일 링크에 넣고, DB에는 `TokenHasher.sha256`의
결과만 저장합니다. DB가 유출돼도 링크를 복원할 수 없습니다.
(`member/application/SignupService.java:40-43`)

## 조회수 중복 방지

| 한국어 | 코드 / 컬럼 | 뜻 |
|---|---|---|
| 조회 기록 | `PostViewLog` / `post_view_log` | 회원이 특정 글을 특정 날짜에 봤다는 기록 |
| 조회 일자 | `viewedOn` / `viewed_on` | `DATE`. 시각이 아니라 날짜 |

PK가 `(post_id, member_id, viewed_on)`이므로 같은 회원이 같은 날 같은 글을 다시 봐도
행이 추가되지 않습니다. `INSERT ... ON CONFLICT DO NOTHING`의 반환값(영향 행 수)이
0이면 이미 본 것, 1이면 처음 본 것입니다.
(`post/domain/PostViewLogRepository.java:11-19`)

## 인프라·설정

| 한국어 | 식별자 | 뜻 |
|---|---|---|
| 저장소 루트 `.env` | `.env` | 비밀값과 PC별 DB 주소. `.gitignore` 대상 |
| 양식 파일 | `.env.example` | 커밋되는 템플릿. 실제 값 금지 |
| 로그 전용 발송기 | `LoggingMailSender` | SMTP 미설정 시 폴백. 본문을 로그로만 남김 |
| 가짜 CAPTCHA | `CAPTCHA_MODE=fake` | 고정 토큰(`dev-captcha`)만 통과시키는 개발용 모드 |
| SPA 폴백 | `SpaResourceConfig` | 정적 파일이 없으면 `index.html`을 돌려주는 딥링크 처리 |

## 아키텍처 용어

| 한국어 | 뜻 |
|---|---|
| 어댑터 인 (`adapter.in.web`) | 외부 요청을 받는 진입점. 컨트롤러와 DTO |
| 애플리케이션 (`application`) | 유스케이스 조립. 트랜잭션 경계 |
| 도메인 (`domain`) | 엔티티와 규칙. 프레임워크 의존 최소화 |
| 어댑터 아웃 (`adapter.out`) | 외부로 나가는 연동. SMTP, CAPTCHA HTTP 호출 |
| 포트 | 애플리케이션이 필요로 하는 외부 기능의 인터페이스. `MailSenderPort`, `CaptchaVerifier` |

## 관련 문서

- [../technology/data/data-model.md](../technology/data/data-model.md) — 컬럼 전체 정의
- [../features/state-machines.md](../features/state-machines.md) — 상태 전이
- [../technology/architecture/components.md](../technology/architecture/components.md) — 패키지 구조
