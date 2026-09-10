# 접근 통제

> 상태: `확인됨` — 코드에서 도출한 실제 권한입니다.
> **문서상 의도가 아니라 실제로 통과하는 것**을 적었습니다.

## 주체 `확인됨`

| 주체 | 판별 | 비고 |
|---|---|---|
| 익명 | `CustomUserDetails == null` | 세션 없음 |
| 회원 (`PENDING`) | 로그인됨, 이메일 미인증 | **회원과 동일 권한** |
| 회원 (`ACTIVE`) | 로그인됨, 인증 완료 | |
| 관리자 | `role == ADMIN` | 생성 경로 없음 |

**`PENDING`과 `ACTIVE`의 권한 차이가 없습니다.**
게시판 판정이 `status`를 보지 않기 때문입니다(`Board.java:44`).

## 게시판별 접근 `확인됨`

V2 시드 + V4 갱신의 현재 상태입니다.

| 게시판 | 익명 읽기 | 익명 쓰기 | 회원 읽기 | 회원 쓰기 | 댓글 | 첨부 |
|---|---|---|---|---|---|---|
| `free` 자유게시판 | 가능 | **가능** | 가능 | 가능 | 불가 | 불가* |
| `qna` Q&A | **불가** | 불가 | 가능 | 가능 | 가능 | 불가* |
| `archive` 자료실 | **불가** | 불가 | 가능 | 가능 | 불가 | 가능 |

\* `allows_attachment = false`이지만 **백엔드가 강제하지 않습니다.**
API를 직접 호출하면 첨부가 붙습니다. `미결정`

판정: `Board.checkReadable` / `checkWritable` (`board/domain/Board.java:43-53`)

## API별 권한 매트릭스 `확인됨`

`○` 가능 · `×` 차단 · `△` 조건부

### 인증

| API | 익명 | 회원 | 관리자 | 차단 지점 |
|---|---|---|---|---|
| `POST /api/auth/login` | ○ | ○ | ○ | — |
| `POST /api/auth/logout` | △ | ○ | ○ | CSRF 토큰 필요 |
| `GET /api/me` | × | ○ | ○ | 계층 1 (`authenticated`) |

### 회원

| API | 익명 | 회원 | 관리자 | 비고 |
|---|---|---|---|---|
| `POST /api/members/signup` | ○ | ○ | ○ | 로그인 상태 검사 없음 `미결정` |
| `GET /api/members/verify-email` | ○ | ○ | ○ | 토큰이 인증 |
| `POST /api/members/verify-email/resend` | ○ | ○ | ○ | 횟수 제한 없음 |
| `POST /api/members/password-reset/request` | ○ | ○ | ○ | CAPTCHA 필요 |
| `POST /api/members/password-reset/change` | ○ | ○ | ○ | 토큰이 인증 |
| `GET /api/members/username-availability` | ○ | ○ | ○ | **존재 여부 노출 (의도됨)** |
| `GET /api/members/nickname-availability` | ○ | ○ | ○ | 동일 |
| `POST /api/members/username-recovery` | ○ | ○ | ○ | **CAPTCHA 없음** |

### 마이페이지

| API | 익명 | 회원 | 관리자 |
|---|---|---|---|
| `GET /api/me/posts` | × | ○ (본인) | ○ (본인) |
| `GET /api/me/comments` | × | ○ (본인) | ○ (본인) |

관리자도 **자기 것만** 봅니다. 다른 회원의 활동을 조회하는 API가 없습니다.

### 게시판·게시글

| API | 익명 | 회원 | 관리자 | 차단 지점 |
|---|---|---|---|---|
| `GET /api/boards` | ○ | ○ | ○ | **검사 없음** |
| `GET /api/boards/{slug}` | △ | ○ | ○ | `Board.checkReadable` |
| `GET /api/boards/{slug}/posts` | △ | ○ | ○ | `Board.checkReadable` |
| `POST /api/boards/{slug}/posts` | △ | ○ | ○ | `Board.checkWritable` |
| `GET /api/posts/{id}` | △ | ○ | ○ | `Board.checkReadable` |
| `PUT /api/posts/{id}` | △ | △ | ○ | `Post.checkEditable` |
| `DELETE /api/posts/{id}` | △ | △ | ○ | `Post.checkEditable` |
| `POST /api/posts/{id}/like` | × | ○ | ○ | 계층 1 |

### 댓글

| API | 익명 | 회원 | 관리자 |
|---|---|---|---|
| `GET /api/posts/{id}/comments` | △ | ○ | ○ |
| `POST /api/posts/{id}/comments` | × | △ | △ |
| `PUT /api/comments/{id}` | × | △ (본인) | ○ |
| `DELETE /api/comments/{id}` | × | △ (본인) | ○ |

댓글 작성의 `△`는 게시판의 `allows_comment`와 `checkWritable`에 따릅니다.

### 파일 — 공백 지대 `확인됨`

| API | 익명 | 회원 | 관리자 | 차단 지점 |
|---|---|---|---|---|
| `POST /api/files` | **○** | ○ | ○ | **없음** |
| `GET /api/files/{storedName}` | **○** | ○ | ○ | **없음** |

**인증도 권한 검사도 없습니다.**

- 업로드: 누구나 100MB × 무제한 (T-002)
- 다운로드: `storedName`(UUID)만 알면 회원제 게시판 첨부도 열람 (T-003)

자료실이 회원제인데 그 첨부는 비회원도 받을 수 있습니다. **정책 모순입니다.**

## 게시글 수정·삭제 판정 상세 `확인됨`

`Post.checkEditable` (`post/domain/Post.java:75-88`)의 전체 경우입니다.

| 요청자 | 글 종류 | 비밀번호 | 결과 |
|---|---|---|---|
| 관리자 | 회원 글 (타인) | — | **통과** |
| 관리자 | 회원 글 (본인) | — | 통과 |
| 관리자 | 비회원 글 | — | **통과** (비밀번호 불필요) |
| 회원 | 회원 글 (본인) | — | 통과 |
| 회원 | 회원 글 (타인) | — | 403 |
| 회원 | 비회원 글 | 정답이어도 | **403** |
| 익명 | 회원 글 | — | 403 |
| 익명 | 비회원 글 | 정답 | 통과 |
| 익명 | 비회원 글 | 오답 또는 `null` | 403 |

### 주목할 두 줄

**6행** — 로그인한 사용자는 비회원 글을 비밀번호로 수정할 수 없습니다.
로그인 분기로 들어가 작성자 일치만 보기 때문입니다.

보안상 옳습니다(추측 공격 차단). 다만 자기가 비회원으로 쓴 글을
로그인 후에는 못 고치는 부작용이 있습니다. `미결정`

**3행** — 관리자는 비회원 글도 비밀번호 없이 수정·삭제합니다.
운영상 필요하지만 **감사 로그가 없어** 누가 무엇을 지웠는지 추적할 수 없습니다.

## 응답에 실리는 권한 힌트 `확인됨`

### `PostResponse.isOwner`의 세 번째 조건

```java
else if (p.getMember() == null && currentMemberId == null) owner = true;
```

(`post/adapter/in/web/dto/PostResponse.java:29-30`)

**비로그인 조회자에게는 모든 비회원 글이 `isOwner = true`로 나옵니다.**
누가 썼든 상관없습니다.

| 관점 | 평가 |
|---|---|
| 보안 | **문제 없음** — 실제 권한은 `Post.checkEditable`의 비밀번호 검증이 막음 |
| UX | 모든 비회원 글에 수정·삭제 버튼이 뜨고, 눌러야 비밀번호가 틀렸음을 앎 |

권한 판정이 응답 DTO가 아니라 도메인에 있기 때문에
이 부정확한 힌트가 보안 결함으로 이어지지 않습니다.
**계층 3이 실질적 방어선이라는 설계가 여기서 효과를 냅니다.**

### `CommentResponse.isOwner`

```java
boolean owner = isAdmin || (c.getMember() != null && currentMemberId != null
                            && c.getMember().getId().equals(currentMemberId));
```

댓글은 비회원 경로가 없어 정확합니다.

## 정책이 실제로 강제되는가 `확인됨`

| 정책 | 선언 위치 | 강제 |
|---|---|---|
| `requires_auth_to_read` | `board` 테이블 | **예** — `Board.checkReadable` |
| `requires_auth_to_write` | `board` 테이블 | **예** — `Board.checkWritable` |
| `allows_comment` | `board` 테이블 | **예** — `CommentService.java:49-51` |
| `allows_attachment` | `board` 테이블 | **아니오** `미결정` |

넷 중 하나가 강제되지 않습니다.
댓글은 막고 첨부는 막지 않는 비대칭이 의도인지 누락인지 불명확합니다.

## 권한 변경 방법 `확인됨`

게시판 정책은 **코드가 아니라 데이터**입니다.

```sql
-- V5__allow_comment_on_free.sql
UPDATE board SET allows_comment = TRUE WHERE slug = 'free';
```

배포 없이 마이그레이션 한 줄로 바뀝니다. V4가 같은 방식이었습니다.

역할·계정 상태는 코드 변경 없이 바꿀 수 없습니다
(`role`을 설정하는 코드가 없음).

## 공백 요약 `제안`

| 공백 | 위험 | 우선순위 |
|---|---|---|
| 파일 업로드 무인증 | DoS, 저장소 오용 | **1** |
| 파일 다운로드 무권한 | 회원제 첨부 유출 | **2** |
| `allows_attachment` 미강제 | 정책 우회 | 3 |
| 관리자 행위 감사 로그 없음 | 추적 불가 | 4 |
| `PENDING`과 `ACTIVE` 무차별 | 인증이 관문 아님 | 5 (결정 필요) |
| 관리자 계정 생성 경로 없음 | 운영 불가 | 6 |
| 아이디 찾기 CAPTCHA 없음 | 열거·남용 | 7 |

## 관련 문서

- [authentication-authorization.md](authentication-authorization.md) — 인증·인가 메커니즘
- [security-architecture.md](security-architecture.md) — 방어 계층
- [threat-model.md](threat-model.md) — T-002, T-003
- [../business/policy-rules.md](../business/policy-rules.md) — 정책 규칙
